package com.dong.dongrag.model.dto.retrieval;

import lombok.Data;

import java.io.Serializable;

@Data
public class RetrievalDetectCase implements Serializable {

    private String question;

    /**
     * Optional gold chunk for Hit@k / MRR; both must be non-null to enable metrics for this case.
     */
    private Long goldDocumentId;

    private Integer goldChunkIndex;

    private static final long serialVersionUID = 1L;
}
