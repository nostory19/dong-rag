package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class IngestionMetricsVO implements Serializable {

    private Long totalJobs;

    private Long successJobs;

    private Long failedJobs;

    private Double failureRate;

    private Double avgDurationSeconds;

    private Map<String, Double> successRateByFileType;

    private Map<Integer, Long> retryCountDistribution;

    private static final long serialVersionUID = 1L;
}
