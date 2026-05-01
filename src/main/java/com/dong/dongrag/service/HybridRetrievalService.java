package com.dong.dongrag.service;

import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;

import java.util.List;

public interface HybridRetrievalService {

    List<ChunkEvidenceVO> hybridRetrieve(Long groupId, String question, int topK);

    HybridRetrievalResultVO retrieveWithJudgement(Long groupId, String question, int topK);
}
