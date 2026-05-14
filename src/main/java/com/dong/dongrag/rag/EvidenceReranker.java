package com.dong.dongrag.rag;

import com.dong.dongrag.model.vo.ChunkEvidenceVO;

import java.util.List;

/**
 * 检索后重排（可选），用于提升送入模型的片段顺序。
 */
public interface EvidenceReranker {

    List<ChunkEvidenceVO> rerank(String question, List<ChunkEvidenceVO> candidates, int topK);
}
