package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.model.dto.qa.QaAskRequest;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.model.vo.QaAnswerVO;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupService;
import com.dong.dongrag.service.HybridRetrievalService;
import com.dong.dongrag.service.RagQaService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagQaServiceImpl implements RagQaService {

    @Resource
    private AuthContextService authContextService;

    @Resource
    private GroupService groupService;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private HybridRetrievalService hybridRetrievalService;

    @Override
    public QaAnswerVO ask(QaAskRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null || StrUtil.isBlank(request.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        int topK = request.getTopK() == null ? 5 : Math.min(10, Math.max(1, request.getTopK()));

        HybridRetrievalResultVO retrieval = hybridRetrievalService.retrieveWithJudgement(request.getGroupId(), request.getQuestion(), topK);
        List<ChunkEvidenceVO> matched = retrieval.getEvidences();

        QaAnswerVO result = new QaAnswerVO();
        if (!retrieval.isEvidenceEnough()) {
            result.setEvidenceEnough(false);
            result.setConfidenceLevel(retrieval.getConfidenceLevel());
            result.setConfidenceScore(retrieval.getConfidenceScore());
            result.setAnswer("未检索到组内有效证据，无法给出可信答案。请补充文档后再试。");
            result.setEvidences(matched == null ? List.of() : matched);
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
        String answer = chatClientBuilder.build().prompt()
                .system("""
                        你是企业知识库问答助手。
                        你只能基于给定证据回答，不允许编造事实。
                        如果证据不够完整，要明确说明不确定性。
                        """)
                .user(userPrompt)
                .call()
                .content();
        result.setAnswer(answer);
        result.setConfidenceScore(retrieval.getConfidenceScore());
        result.setConfidenceLevel(retrieval.getConfidenceLevel());
        result.setEvidences(matched);
        return result;
    }
}
