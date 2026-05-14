package com.dong.dongrag.model.dto.retrieval;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RetrievalDetectRequest implements Serializable {

    private Long groupId;

    private Integer topK;

    private List<RetrievalDetectCase> cases;

    /**
     * 为 true 且开启 {@code dongrag.ai.retrieval-rerank-enabled} 时，对金标用例额外计算未重排基线 rank/Hit/MRR。
     */
    private Boolean includeRerankComparison;

    private static final long serialVersionUID = 1L;
}
