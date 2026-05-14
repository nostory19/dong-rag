package com.dong.dongrag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.dong.dongrag.common.BaseResponse;
import com.dong.dongrag.common.ResultUtils;
import com.dong.dongrag.model.dto.document.IngestTextRequest;
import com.dong.dongrag.model.dto.qa.QaAskRequest;
import com.dong.dongrag.model.dto.retrieval.RetrievalDetectRequest;
import com.dong.dongrag.model.vo.RetrievalDetectResponseVO;
import com.dong.dongrag.model.vo.IngestionJobVO;
import com.dong.dongrag.model.vo.IngestionMetricsVO;
import com.dong.dongrag.model.vo.QaAnswerVO;
import com.dong.dongrag.model.vo.IngestionTaskVO;
import com.dong.dongrag.service.IngestionJobService;
import com.dong.dongrag.service.RagIngestionService;
import com.dong.dongrag.service.RagQaService;
import com.dong.dongrag.service.RetrievalDetectionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rag")
@SaCheckLogin
public class RagController {

    @Resource
    private RagIngestionService ragIngestionService;

    @Resource
    private RagQaService ragQaService;

    @Resource
    private IngestionJobService ingestionJobService;

    @Resource
    private RetrievalDetectionService retrievalDetectionService;

    @PostMapping("/ingest/text")
    public BaseResponse<IngestionTaskVO> ingestText(@RequestBody IngestTextRequest request) {
        return ResultUtils.success(ragIngestionService.ingestPlainText(request));
    }

    @PostMapping("/ingest/file")
    public BaseResponse<IngestionTaskVO> ingestFile(@RequestParam("groupId") Long groupId,
                                                    @RequestParam("file") MultipartFile file) {
        return ResultUtils.success(ragIngestionService.ingestFile(groupId, file));
    }

    @GetMapping("/ingest/task/{jobId}")
    public BaseResponse<IngestionTaskVO> getTaskStatus(@PathVariable Long jobId) {
        return ResultUtils.success(ragIngestionService.getTaskStatus(jobId));
    }

    @PostMapping("/qa/ask")
    public BaseResponse<QaAnswerVO> ask(@RequestBody QaAskRequest request) {
        return ResultUtils.success(ragQaService.ask(request));
    }

    /**
     * 管理端：批量检索检测（混合检索 + 可选金标 Hit@k / MRR）。
     */
    @PostMapping("/detect/retrieval")
    @SaCheckRole("admin")
    public BaseResponse<RetrievalDetectResponseVO> detectRetrieval(@RequestBody RetrievalDetectRequest request) {
        return ResultUtils.success(retrievalDetectionService.detect(request));
    }

    @GetMapping("/ingest/jobs")
    @SaCheckRole("admin")
    public BaseResponse<java.util.List<IngestionJobVO>> listJobs(@RequestParam(value = "limit", required = false) Integer limit) {
        return ResultUtils.success(ingestionJobService.listJobs(limit));
    }

    @GetMapping("/ingest/jobs/{jobId}")
    @SaCheckRole("admin")
    public BaseResponse<IngestionJobVO> getJob(@PathVariable Long jobId) {
        return ResultUtils.success(ingestionJobService.getJobDetail(jobId));
    }

    @PostMapping("/ingest/jobs/{jobId}/retry")
    @SaCheckRole("admin")
    public BaseResponse<Boolean> retryJob(@PathVariable Long jobId) {
        ingestionJobService.retryJob(jobId);
        return ResultUtils.success(true);
    }

    @PostMapping("/ingest/documents/{documentId}/rebuild")
    @SaCheckRole("admin")
    public BaseResponse<Boolean> rebuildDocument(@PathVariable Long documentId) {
        ragIngestionService.rebuildDocumentIndexes(documentId);
        return ResultUtils.success(true);
    }

    @GetMapping("/ingest/metrics")
    @SaCheckRole("admin")
    public BaseResponse<IngestionMetricsVO> ingestionMetrics() {
        return ResultUtils.success(ingestionJobService.getMetrics());
    }
}
