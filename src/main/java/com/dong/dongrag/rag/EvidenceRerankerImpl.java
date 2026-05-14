package com.dong.dongrag.rag;

import com.dong.dongrag.config.DongragAiProperties;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EvidenceRerankerImpl implements EvidenceReranker {

    private static final Logger log = LoggerFactory.getLogger(EvidenceRerankerImpl.class);
    private static final int SNIPPET_LEN = 320;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private DongragAiProperties dongragAiProperties;

    @Override
    public List<ChunkEvidenceVO> rerank(String question, List<ChunkEvidenceVO> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (!dongragAiProperties.isRetrievalRerankEnabled()) {
            return candidates.stream().limit(topK).toList();
        }
        int limit = Math.min(candidates.size(), Math.max(1, dongragAiProperties.getRerankCandidateLimit()));
        List<ChunkEvidenceVO> slice = candidates.subList(0, limit);
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < slice.size(); i++) {
            ChunkEvidenceVO c = slice.get(i);
            String body = c.getContent() == null ? "" : c.getContent();
            if (body.length() > SNIPPET_LEN) {
                body = body.substring(0, SNIPPET_LEN) + "…";
            }
            numbered.append("[").append(i).append("] doc=").append(c.getDocumentId())
                    .append(" chunk=").append(c.getChunkIndex()).append("\n")
                    .append(body).append("\n\n");
        }
        try {
            String raw = chatClientBuilder.build().prompt()
                    .system("""
                            你是检索重排器。根据用户问题，将下面编号片段按相关性从高到低排序。
                            只输出 JSON 数组：整数下标，例如 [2,0,1]。不要解释。""")
                    .user("问题:\n" + question + "\n\n片段:\n" + numbered)
                    .call()
                    .content();
            String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode arr = objectMapper.readTree(cleaned);
            if (!arr.isArray() || arr.isEmpty()) {
                return candidates.stream().limit(topK).toList();
            }
            List<ChunkEvidenceVO> ordered = new ArrayList<>();
            for (JsonNode n : arr) {
                int idx = n.asInt(-1);
                if (idx >= 0 && idx < slice.size()) {
                    ChunkEvidenceVO vo = slice.get(idx);
                    if (!ordered.contains(vo)) {
                        ordered.add(vo);
                    }
                }
            }
            for (ChunkEvidenceVO vo : slice) {
                if (!ordered.contains(vo)) {
                    ordered.add(vo);
                }
            }
            return ordered.stream().limit(topK).toList();
        } catch (Exception e) {
            log.warn("LLM rerank failed, fallback to original order: {}", e.getMessage());
            return candidates.stream().limit(topK).toList();
        }
    }
}
