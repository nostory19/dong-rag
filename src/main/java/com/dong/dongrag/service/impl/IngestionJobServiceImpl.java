package com.dong.dongrag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.IngestionJobMapper;
import com.dong.dongrag.mapper.KnowledgeDocumentMapper;
import com.dong.dongrag.model.entity.IngestionJob;
import com.dong.dongrag.model.entity.KnowledgeDocument;
import com.dong.dongrag.model.vo.IngestionJobVO;
import com.dong.dongrag.model.vo.IngestionMetricsVO;
import com.dong.dongrag.service.IngestionJobService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class IngestionJobServiceImpl implements IngestionJobService {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobServiceImpl.class);

    private static final String JOB_TYPE_INGEST = "INGEST";
    private static final String JOB_PENDING = "PENDING";
    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_SUCCESS = "SUCCESS";
    private static final String JOB_FAILED = "FAILED";
    private static final String JOB_RETRY_WAITING = "RETRY_WAITING";

    @Resource
    private IngestionJobMapper ingestionJobMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public IngestionJob createJob(KnowledgeDocument document) {
        IngestionJob job = new IngestionJob();
        job.setDocumentId(document.getId());
        job.setGroupId(document.getGroupId());
        job.setJobType(JOB_TYPE_INGEST);
        job.setStatus(JOB_PENDING);
        job.setRetryCount(0);
        job.setMaxRetries(3);
        job.setNextRetryAt(LocalDateTime.now());
        ingestionJobMapper.insert(job);
        log.info("Ingestion job created, jobId={}, documentId={}, groupId={}", job.getId(), job.getDocumentId(), job.getGroupId());
        return job;
    }

    @Override
    public List<IngestionJob> pollRunnableJobs(int limit) {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<IngestionJob> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("status", JOB_PENDING)
                        .or()
                        .and(n -> n.eq("status", JOB_RETRY_WAITING).le("next_retry_at", now)))
                .orderByAsc("created_at")
                .last("limit " + Math.max(1, limit));
        return ingestionJobMapper.selectList(wrapper);
    }

    @Override
    public boolean tryMarkRunning(Long jobId, String workerId) {
        UpdateWrapper<IngestionJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .in("status", JOB_PENDING, JOB_RETRY_WAITING)
                .set("status", JOB_RUNNING)
                .set("worker_id", workerId)
                .set("started_at", LocalDateTime.now())
                .set("finished_at", null)
                .set("last_error", null);
        boolean locked = ingestionJobMapper.update(null, wrapper) > 0;
        if (locked) {
            log.info("Ingestion job locked by worker, jobId={}, workerId={}", jobId, workerId);
        }
        return locked;
    }

    @Override
    public void markSuccess(Long jobId) {
        UpdateWrapper<IngestionJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .set("status", JOB_SUCCESS)
                .set("finished_at", LocalDateTime.now())
                .set("next_retry_at", null)
                .set("last_error", null);
        ingestionJobMapper.update(null, wrapper);
        log.info("Ingestion job marked success, jobId={}", jobId);
    }

    @Override
    public void markRetryWaiting(Long jobId, String errorMessage) {
        IngestionJob job = getById(jobId);
        int nextRetryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        if (nextRetryCount > (job.getMaxRetries() == null ? 0 : job.getMaxRetries())) {
            log.warn("Ingestion job retries exceeded, mark failed, jobId={}, retryCount={}", jobId, nextRetryCount);
            markFailed(jobId, errorMessage);
            return;
        }
        int backoffSeconds = (int) Math.min(300, Math.pow(2, nextRetryCount) * 5);
        UpdateWrapper<IngestionJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .set("status", JOB_RETRY_WAITING)
                .set("retry_count", nextRetryCount)
                .set("finished_at", LocalDateTime.now())
                .set("next_retry_at", LocalDateTime.now().plusSeconds(backoffSeconds))
                .set("last_error", trimError(errorMessage));
        ingestionJobMapper.update(null, wrapper);
        log.warn("Ingestion job waiting for retry, jobId={}, retryCount={}, nextRetryAt={}, error={}",
                jobId, nextRetryCount, LocalDateTime.now().plusSeconds(backoffSeconds), trimError(errorMessage));
    }

    @Override
    public void markFailed(Long jobId, String errorMessage) {
        UpdateWrapper<IngestionJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .set("status", JOB_FAILED)
                .set("finished_at", LocalDateTime.now())
                .set("next_retry_at", null)
                .set("last_error", trimError(errorMessage));
        ingestionJobMapper.update(null, wrapper);
        log.error("Ingestion job marked failed, jobId={}, error={}", jobId, trimError(errorMessage));
    }

    @Override
    public IngestionJob getById(Long jobId) {
        IngestionJob job = ingestionJobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "入库任务不存在");
        }
        return job;
    }

    @Override
    public IngestionJobVO getJobDetail(Long jobId) {
        return toVO(getById(jobId));
    }

    @Override
    public List<IngestionJobVO> listJobs(Integer limit) {
        QueryWrapper<IngestionJob> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id").last("limit " + Math.max(1, Objects.requireNonNullElse(limit, 50)));
        return ingestionJobMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public void retryJob(Long jobId) {
        IngestionJob job = getById(jobId);
        if (!JOB_FAILED.equals(job.getStatus()) && !JOB_RETRY_WAITING.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前任务状态不允许重试");
        }
        UpdateWrapper<IngestionJob> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", jobId)
                .set("status", JOB_PENDING)
                .set("next_retry_at", LocalDateTime.now())
                .set("last_error", null);
        ingestionJobMapper.update(null, wrapper);
        log.info("Ingestion job retried manually, jobId={}", jobId);
    }

    @Override
    public IngestionMetricsVO getMetrics() {
        List<IngestionJob> jobs = ingestionJobMapper.selectList(new QueryWrapper<IngestionJob>());
        IngestionMetricsVO metricsVO = new IngestionMetricsVO();
        long total = jobs.size();
        long success = jobs.stream().filter(j -> JOB_SUCCESS.equals(j.getStatus())).count();
        long failed = jobs.stream().filter(j -> JOB_FAILED.equals(j.getStatus())).count();
        metricsVO.setTotalJobs(total);
        metricsVO.setSuccessJobs(success);
        metricsVO.setFailedJobs(failed);
        metricsVO.setFailureRate(total == 0 ? 0D : (double) failed / total);

        double avgDuration = jobs.stream()
                .filter(j -> j.getStartedAt() != null && j.getFinishedAt() != null)
                .mapToLong(j -> Duration.between(j.getStartedAt(), j.getFinishedAt()).toSeconds())
                .average()
                .orElse(0D);
        metricsVO.setAvgDurationSeconds(avgDuration);

        Map<Long, String> docFileTypeMap = knowledgeDocumentMapper.selectList(new QueryWrapper<KnowledgeDocument>())
                .stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d.getFileExt() == null ? "unknown" : d.getFileExt(), (a, b) -> a));
        Map<String, List<IngestionJob>> byType = jobs.stream()
                .collect(Collectors.groupingBy(job -> docFileTypeMap.getOrDefault(job.getDocumentId(), "unknown")));
        Map<String, Double> successRateByFileType = new LinkedHashMap<>();
        byType.forEach((type, typeJobs) -> {
            long typeSuccess = typeJobs.stream().filter(j -> JOB_SUCCESS.equals(j.getStatus())).count();
            successRateByFileType.put(type, typeJobs.isEmpty() ? 0D : (double) typeSuccess / typeJobs.size());
        });
        metricsVO.setSuccessRateByFileType(successRateByFileType);

        Map<Integer, Long> retryDistribution = jobs.stream()
                .collect(Collectors.groupingBy(j -> j.getRetryCount() == null ? 0 : j.getRetryCount(), Collectors.counting()));
        metricsVO.setRetryCountDistribution(retryDistribution);
        return metricsVO;
    }

    private IngestionJobVO toVO(IngestionJob job) {
        IngestionJobVO vo = new IngestionJobVO();
        vo.setId(job.getId());
        vo.setDocumentId(job.getDocumentId());
        vo.setGroupId(job.getGroupId());
        vo.setJobType(job.getJobType());
        vo.setStatus(job.getStatus());
        vo.setRetryCount(job.getRetryCount());
        vo.setMaxRetries(job.getMaxRetries());
        vo.setStartedAt(job.getStartedAt());
        vo.setFinishedAt(job.getFinishedAt());
        vo.setNextRetryAt(job.getNextRetryAt());
        vo.setLastError(job.getLastError());
        return vo;
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }
}
