package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class QaAnswerVO implements Serializable {

    private String answer;

    private boolean evidenceEnough;

    private String confidenceLevel;

    private Double confidenceScore;

    private List<ChunkEvidenceVO> evidences;

    /**
     * 是否来自 Redis 答案缓存。
     */
    private Boolean fromCache;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private static final long serialVersionUID = 1L;
}
