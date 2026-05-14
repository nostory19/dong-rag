package com.dong.dongrag.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class RetrievalDetectCaseResultVO implements Serializable {

    private String question;

    private Double confidenceScore;

    private String confidenceLevel;

    private Boolean evidenceEnough;

    /**
     * 1-based rank of gold chunk in hybrid list; null if no gold or not found.
     */
    private Integer rankOfGold;

    private Boolean hitAt1;

    private Boolean hitAtK;

    private Double reciprocalRank;

    private Long goldDocumentId;

    private Integer goldChunkIndex;

    private Boolean labeled;

    private String error;

    private List<ChunkEvidenceVO> evidences;

    /**
     * 未做 LLM 重排时的金标 rank（仅 includeRerankComparison 且开启重排时有值）。
     */
    private Integer rankOfGoldBaseline;

    private Boolean hitAtKBaseline;

    private Double reciprocalRankBaseline;

    private static final long serialVersionUID = 1L;
}
