package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class IngestionJobVO implements Serializable {

    private Long id;

    private Long documentId;

    private Long groupId;

    private String jobType;

    private String status;

    private Integer retryCount;

    private Integer maxRetries;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private static final long serialVersionUID = 1L;
}
