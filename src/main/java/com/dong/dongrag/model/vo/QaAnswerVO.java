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

    private static final long serialVersionUID = 1L;
}
