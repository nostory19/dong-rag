package com.dong.dongrag.service;

import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;

import java.util.List;

public interface HybridRetrievalService {

    default List<ChunkEvidenceVO> hybridRetrieve(Long groupId, String question, int topK) {
        return hybridRetrieve(groupId, question, topK, true);
    }

    /**
     * @param applyRerank 为 false 时跳过 LLM 重排（用于检测对比基线等）。
     */
    List<ChunkEvidenceVO> hybridRetrieve(Long groupId, String question, int topK, boolean applyRerank);

    HybridRetrievalResultVO retrieveWithJudgement(Long groupId, String question, int topK);
}
