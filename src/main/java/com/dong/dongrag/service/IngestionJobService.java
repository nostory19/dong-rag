package com.dong.dongrag.service;

import com.dong.dongrag.model.entity.IngestionJob;
import com.dong.dongrag.model.entity.KnowledgeDocument;
import com.dong.dongrag.model.vo.IngestionJobVO;
import com.dong.dongrag.model.vo.IngestionMetricsVO;

import java.util.List;

public interface IngestionJobService {

    IngestionJob createJob(KnowledgeDocument document);

    List<IngestionJob> pollRunnableJobs(int limit);

    boolean tryMarkRunning(Long jobId, String workerId);

    void markSuccess(Long jobId);

    void markRetryWaiting(Long jobId, String errorMessage);

    void markFailed(Long jobId, String errorMessage);

    IngestionJob getById(Long jobId);

    IngestionJobVO getJobDetail(Long jobId);

    List<IngestionJobVO> listJobs(Integer limit);

    void retryJob(Long jobId);

    IngestionMetricsVO getMetrics();
}
