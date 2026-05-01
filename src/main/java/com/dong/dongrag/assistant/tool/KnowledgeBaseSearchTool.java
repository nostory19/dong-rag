package com.dong.dongrag.assistant.tool;

import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.service.HybridRetrievalService;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseSearchTool {

    @Resource
    private HybridRetrievalService hybridRetrievalService;

    @Resource
    private ToolCallTraceContext toolCallTraceContext;

    @Tool(name = "KB_SEARCH", description = "在指定 groupId 的知识库中检索证据，只返回证据和置信度，不直接生成答案")
    public HybridRetrievalResultVO search(Long groupId, String question, Integer topK) {
        int size = topK == null ? 5 : topK;
        toolCallTraceContext.log("KB_SEARCH start: groupId=%s, topK=%s, question=%s"
                .formatted(groupId, size, shorten(question)));
        HybridRetrievalResultVO result = hybridRetrievalService.retrieveWithJudgement(groupId, question, size);
        int evidenceCount = result.getEvidences() == null ? 0 : result.getEvidences().size();
        toolCallTraceContext.log("KB_SEARCH done: evidenceEnough=%s, confidence=%.4f, evidences=%d"
                .formatted(result.isEvidenceEnough(), result.getConfidenceScore(), evidenceCount));
        return result;
    }

    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
