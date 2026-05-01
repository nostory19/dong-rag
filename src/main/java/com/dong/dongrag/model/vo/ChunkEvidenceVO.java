package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChunkEvidenceVO implements Serializable {

    private Long documentId;

    private Integer chunkIndex;

    private String fileName;

    private String content;

    private Integer charStart;

    private Integer charEnd;

    private Double score;

    private String source;

    private static final long serialVersionUID = 1L;
}
