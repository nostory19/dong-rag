package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.dong.dongrag.config.DongragAiProperties;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.model.dto.qa.QaAskRequest;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.model.vo.QaAnswerVO;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupKnowledgeRevisionService;
import com.dong.dongrag.service.GroupService;
import com.dong.dongrag.service.HybridRetrievalService;
import com.dong.dongrag.service.RagQaService;
import com.dong.dongrag.support.PromptResourceLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RagQaServiceImpl implements RagQaService {

    private static final String RAG_QA_SYSTEM_FALLBACK = """
            你是企业知识库问答助手。
            你只能基于给定证据回答，不允许编造事实。
            如果证据不够完整，要明确说明不确定性。
            """;

    @Resource
    private AuthContextService authContextService;

    @Resource
    private GroupService groupService;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private HybridRetrievalService hybridRetrievalService;

    @Resource
    private DongragAiProperties dongragAiProperties;

    @Resource
    private GroupKnowledgeRevisionService groupKnowledgeRevisionService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PromptResourceLoader promptResourceLoader;

    @Resource
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Resource
    private MeterRegistry meterRegistry;

    @Override
    public QaAnswerVO ask(QaAskRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null || StrUtil.isBlank(request.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        int topK = request.getTopK() == null ? 5 : Math.min(10, Math.max(1, request.getTopK()));

        int ttl = dongragAiProperties.getQaAnswerCacheTtlSeconds();
        if (ttl > 0) {
            try {
                String cacheKey = buildCacheKey(request.getGroupId(), topK, request.getQuestion());
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StrUtil.isNotBlank(cached)) {
                    meterRegistry.counter("dongrag.rag.qa.cache", "result", "hit").increment();
                    return objectMapper.readValue(cached, QaAnswerVO.class);
                }
            } catch (Exception e) {
                // ignore cache read errors
            }
        }

        HybridRetrievalResultVO retrieval = hybridRetrievalService.retrieveWithJudgement(request.getGroupId(), request.getQuestion(), topK);
        List<ChunkEvidenceVO> matched = retrieval.getEvidences();

        QaAnswerVO result = new QaAnswerVO();
        if (!retrieval.isEvidenceEnough()) {
            result.setEvidenceEnough(false);
            result.setConfidenceLevel(retrieval.getConfidenceLevel());
            result.setConfidenceScore(retrieval.getConfidenceScore());
            result.setAnswer("未检索到组内有效证据，无法给出可信答案。请补充文档后再试。");
            result.setEvidences(matched == null ? List.of() : matched);
            result.setFromCache(false);
            return result;
        }
        result.setEvidenceEnough(true);
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            ChunkEvidenceVO evidence = matched.get(i);
            contextBuilder.append("证据").append(i + 1).append(" (文件:")
                    .append(evidence.getFileName())
                    .append(", 范围:")
                    .append(evidence.getCharStart())
                    .append("-")
                    .append(evidence.getCharEnd())
                    .append("):\n")
                    .append(evidence.getContent())
                    .append("\n\n");
        }
        String userPrompt = "问题:\n" + request.getQuestion() + "\n\n证据:\n" + contextBuilder;
        String systemPrompt = promptResourceLoader.loadOrDefault("prompts/rag-qa-system.txt", RAG_QA_SYSTEM_FALLBACK);

        Timer.Sample llmSample = Timer.start(meterRegistry);
        ChatResponse chatResponse;
        try {
            if (dongragAiProperties.isLlmCircuitBreakerEnabled()) {
                chatResponse = circuitBreakerRegistry.circuitBreaker("llm")
                        .executeSupplier(() -> chatClientBuilder.build().prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .call()
                                .chatResponse());
            } else {
                chatResponse = chatClientBuilder.build().prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .chatResponse();
            }
        } finally {
            llmSample.stop(Timer.builder("dongrag.rag.qa.llm")
                    .description("RAG QA LLM generation")
                    .register(meterRegistry));
        }

        AssistantMessage out = chatResponse.getResult().getOutput();
        String answer = out.getText();
        result.setAnswer(answer);
        result.setConfidenceScore(retrieval.getConfidenceScore());
        result.setConfidenceLevel(retrieval.getConfidenceLevel());
        result.setEvidences(matched);
        result.setFromCache(false);
        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            var u = chatResponse.getMetadata().getUsage();
            result.setPromptTokens(u.getPromptTokens());
            result.setCompletionTokens(u.getCompletionTokens());
            result.setTotalTokens(u.getTotalTokens());
        }

        if (ttl > 0) {
            try {
                String cacheKey = buildCacheKey(request.getGroupId(), topK, request.getQuestion());
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                        Duration.ofSeconds(ttl));
                meterRegistry.counter("dongrag.rag.qa.cache_write").increment();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String buildCacheKey(Long groupId, int topK, String question) {
        String fp = groupKnowledgeRevisionService.fingerprint(groupId);
        String qh = DigestUtil.sha256Hex(question.trim() + "|topK=" + topK);
        return "dongrag:qa:v1:" + fp + ":" + groupId + ":" + qh;
    }
}
