package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class IngestionTaskVO implements Serializable {

    private Long documentId;

    private Long jobId;

    private String documentStatus;

    private String jobStatus;

    private String failureReason;

    private String lastError;

    private static final long serialVersionUID = 1L;
}
