package com.dong.dongrag.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class RetrievalDetectResponseVO implements Serializable {

    private int caseCount;

    private int labeledCount;

    private Double meanHitAt1;

    private Double meanHitAtK;

    private Double mrr;

    private Double meanHitAt1Baseline;

    private Double meanHitAtKBaseline;

    private Double mrrBaseline;

    private List<RetrievalDetectCaseResultVO> details;

    private static final long serialVersionUID = 1L;
}
