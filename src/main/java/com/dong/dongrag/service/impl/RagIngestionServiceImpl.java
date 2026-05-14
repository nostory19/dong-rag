package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.DocumentChunkMapper;
import com.dong.dongrag.mapper.KnowledgeDocumentMapper;
import com.dong.dongrag.model.dto.document.IngestTextRequest;
import com.dong.dongrag.model.entity.DocumentChunk;
import com.dong.dongrag.model.entity.IngestionJob;
import com.dong.dongrag.model.entity.KnowledgeDocument;
import com.dong.dongrag.model.es.RagChunkIndex;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.model.vo.IngestionTaskVO;
import com.dong.dongrag.repository.RagChunkIndexRepository;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupKnowledgeRevisionService;
import com.dong.dongrag.service.GroupService;
import com.dong.dongrag.service.HybridRetrievalService;
import com.dong.dongrag.service.IngestionJobService;
import com.dong.dongrag.service.MinioStorageService;
import com.dong.dongrag.service.RagIngestionService;
import jakarta.annotation.Resource;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RagIngestionServiceImpl implements RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionServiceImpl.class);

    private static final String DOC_UPLOADED = "UPLOADED";
    private static final String DOC_PROCESSING = "PROCESSING";
    private static final String DOC_READY = "READY";
    private static final String DOC_FAILED = "FAILED";

    @Resource
    private AuthContextService authContextService;

    @Resource
    private GroupService groupService;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private DocumentChunkMapper documentChunkMapper;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private RagChunkIndexRepository ragChunkIndexRepository;

    @Resource
    private IngestionJobService ingestionJobService;

    @Resource
    private HybridRetrievalService hybridRetrievalService;

    @Resource
    private GroupKnowledgeRevisionService groupKnowledgeRevisionService;

    @Resource
    private MinioStorageService minioStorageService;

    @Resource
    @Qualifier("applicationTaskExecutor")
    private TaskExecutor taskExecutor;

    private final Tika tika = new Tika();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IngestionTaskVO ingestPlainText(IngestTextRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null || StrUtil.hasBlank(request.getFileName(), request.getContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        String fileExt = extractFileExt(request.getFileName());
        byte[] rawBytes = request.getContent().getBytes(StandardCharsets.UTF_8);
        String storageKey = buildStorageKey(request.getGroupId(), request.getFileName());
        minioStorageService.upload(storageKey, new ByteArrayInputStream(rawBytes), rawBytes.length, "text/plain");

        KnowledgeDocument knowledgeDocument = new KnowledgeDocument();
        knowledgeDocument.setGroupId(request.getGroupId());
        knowledgeDocument.setUploaderUserId(userId);
        knowledgeDocument.setFileName(request.getFileName());
        knowledgeDocument.setFileExt(fileExt);
        knowledgeDocument.setContentType("text/plain");
        knowledgeDocument.setFileSize((long) rawBytes.length);
        knowledgeDocument.setStorageBucket(minioStorageService.getBucketName());
        knowledgeDocument.setStorageObjectKey(storageKey);
        knowledgeDocument.setStatus(DOC_UPLOADED);
        knowledgeDocument.setDeleted(false);
        knowledgeDocument.setFailureReason(null);
        knowledgeDocument.setContentHash(sha256(request.getContent()));
        knowledgeDocument.setUploadedAt(LocalDateTime.now());
        knowledgeDocumentMapper.insert(knowledgeDocument);

        IngestionJob job = ingestionJobService.createJob(knowledgeDocument);
        log.info("Ingestion task created from text upload, documentId={}, jobId={}, groupId={}, fileName={}",
                knowledgeDocument.getId(), job.getId(), request.getGroupId(), request.getFileName());
        triggerJobAsync(job.getId(), "upload-text");

        IngestionTaskVO vo = new IngestionTaskVO();
        vo.setDocumentId(knowledgeDocument.getId());
        vo.setJobId(job.getId());
        vo.setDocumentStatus(knowledgeDocument.getStatus());
        vo.setJobStatus(job.getStatus());
        vo.setFailureReason(knowledgeDocument.getFailureReason());
        vo.setLastError(job.getLastError());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IngestionTaskVO ingestFile(Long groupId, MultipartFile file) {
        if (groupId == null || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传有效文件");
        }
        Long userId = authContextService.requireLoginUserId();
        groupService.checkGroupReadable(userId, groupId);
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.txt");
        String fileExt = extractFileExt(filename);
        String storageKey = buildStorageKey(groupId, filename);
        try (InputStream inputStream = file.getInputStream()) {
            minioStorageService.upload(storageKey, inputStream, file.getSize(), defaultContentType(file.getContentType()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件读取失败");
        }

        KnowledgeDocument knowledgeDocument = new KnowledgeDocument();
        knowledgeDocument.setGroupId(groupId);
        knowledgeDocument.setUploaderUserId(userId);
        knowledgeDocument.setFileName(filename);
        knowledgeDocument.setFileExt(fileExt);
        knowledgeDocument.setContentType(defaultContentType(file.getContentType()));
        knowledgeDocument.setFileSize(file.getSize());
        knowledgeDocument.setStorageBucket(minioStorageService.getBucketName());
        knowledgeDocument.setStorageObjectKey(storageKey);
        knowledgeDocument.setStatus(DOC_UPLOADED);
        knowledgeDocument.setDeleted(false);
        knowledgeDocument.setFailureReason(null);
        knowledgeDocument.setContentHash(null);
        knowledgeDocument.setUploadedAt(LocalDateTime.now());
        knowledgeDocumentMapper.insert(knowledgeDocument);

        IngestionJob job = ingestionJobService.createJob(knowledgeDocument);
        log.info("Ingestion task created from file upload, documentId={}, jobId={}, groupId={}, fileName={}",
                knowledgeDocument.getId(), job.getId(), groupId, filename);
        triggerJobAsync(job.getId(), "upload-file");

        IngestionTaskVO vo = new IngestionTaskVO();
        vo.setDocumentId(knowledgeDocument.getId());
        vo.setJobId(job.getId());
        vo.setDocumentStatus(knowledgeDocument.getStatus());
        vo.setJobStatus(job.getStatus());
        vo.setFailureReason(knowledgeDocument.getFailureReason());
        vo.setLastError(job.getLastError());
        return vo;
    }

    @Override
    public IngestionTaskVO getTaskStatus(Long jobId) {
        Long userId = authContextService.requireLoginUserId();
        IngestionJob job = ingestionJobService.getById(jobId);
        groupService.checkGroupReadable(userId, job.getGroupId());
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(job.getDocumentId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "关联文档不存在");
        }
        IngestionTaskVO vo = new IngestionTaskVO();
        vo.setDocumentId(document.getId());
        vo.setJobId(job.getId());
        vo.setDocumentStatus(document.getStatus());
        vo.setJobStatus(job.getStatus());
        vo.setFailureReason(document.getFailureReason());
        vo.setLastError(job.getLastError());
        return vo;
    }

    @Override
    public void processIngestionJob(Long jobId) {
        IngestionJob job = ingestionJobService.getById(jobId);
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(job.getDocumentId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            log.warn("Ingestion job skipped due to missing document, jobId={}, documentId={}", jobId, job.getDocumentId());
            ingestionJobService.markFailed(jobId, "关联文档不存在或已删除");
            return;
        }
        log.info("Ingestion job started, jobId={}, documentId={}, groupId={}, fileName={}",
                jobId, document.getId(), document.getGroupId(), document.getFileName());
        markDocumentProcessing(document.getId());
        try {
            executePipeline(jobId, document);
            markDocumentReady(document.getId());
            ingestionJobService.markSuccess(jobId);
            log.info("Ingestion job completed successfully, jobId={}, documentId={}", jobId, document.getId());
        } catch (Exception e) {
            String errorMsg = e.getMessage() == null ? "未知异常" : e.getMessage();
            markDocumentFailure(document.getId(), errorMsg);
            ingestionJobService.markRetryWaiting(jobId, errorMsg);
            log.error("Ingestion job failed, jobId={}, documentId={}, error={}", jobId, document.getId(), errorMsg, e);
        }
    }

    @Override
    public void rebuildDocumentIndexes(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        markDocumentProcessing(document.getId());
        executePipeline(null, document);
        markDocumentReady(document.getId());
    }

    @Scheduled(fixedDelay = 3000L)
    public void dispatchPendingIngestionJobs() {
        List<IngestionJob> jobs = ingestionJobService.pollRunnableJobs(3);
        if (!jobs.isEmpty()) {
            log.info("Scheduler fetched ingestion jobs, size={}", jobs.size());
        }
        String workerId = "scheduler-" + UUID.randomUUID().toString().substring(0, 8);
        for (IngestionJob job : jobs) {
            boolean locked = ingestionJobService.tryMarkRunning(job.getId(), workerId);
            if (!locked) {
                log.debug("Skip ingestion job due to lock conflict, jobId={}", job.getId());
                continue;
            }
            processIngestionJob(job.getId());
        }
    }

    private void triggerJobAsync(Long jobId, String source) {
        taskExecutor.execute(() -> {
            String workerId = source + "-" + UUID.randomUUID().toString().substring(0, 8);
            boolean locked = ingestionJobService.tryMarkRunning(jobId, workerId);
            if (!locked) {
                log.debug("Async trigger skipped due to lock conflict, source={}, jobId={}", source, jobId);
                return;
            }
            log.info("Async trigger accepted, source={}, jobId={}, workerId={}", source, jobId, workerId);
            processIngestionJob(jobId);
        });
    }

    private void executePipeline(Long jobId, KnowledgeDocument document) {
        log.info("Pipeline step: parse and normalize, jobId={}, documentId={}", jobId, document.getId());
        String content = parseDocumentContent(document);
        String normalized = normalizeContent(content, document.getFileExt());
        validateContentQuality(normalized);
        updateDocumentContentHash(document.getId(), sha256(normalized));
        log.info("Pipeline step: split chunks, jobId={}, documentId={}, textLength={}", jobId, document.getId(), normalized.length());
        List<ChunkPart> chunkParts = splitToChunks(normalized, 500, 80);
        if (chunkParts.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文本切片为空");
        }
        log.info("Pipeline step: upsert chunks, jobId={}, documentId={}, chunkCount={}", jobId, document.getId(), chunkParts.size());
        List<DocumentChunk> chunks = upsertChunks(document, chunkParts);
        log.info("Pipeline step: vector indexing, jobId={}, documentId={}, chunkCount={}", jobId, document.getId(), chunks.size());
        indexVector(document, chunks);
        log.info("Pipeline step: es indexing, jobId={}, documentId={}, chunkCount={}", jobId, document.getId(), chunks.size());
        indexEs(document, chunks);
        log.info("Pipeline step: consistency verification, jobId={}, documentId={}", jobId, document.getId());
        verifyConsistency(document, chunks.size());
    }

    @Transactional(rollbackFor = Exception.class)
    protected List<DocumentChunk> upsertChunks(KnowledgeDocument document, List<ChunkPart> chunkParts) {
        List<DocumentChunk> result = new ArrayList<>();
        for (int i = 0; i < chunkParts.size(); i++) {
            ChunkPart chunkPart = chunkParts.get(i);
            DocumentChunk existing = documentChunkMapper.selectOne(new QueryWrapper<DocumentChunk>()
                    .eq("document_id", document.getId())
                    .eq("chunk_index", i)
                    .last("limit 1"));
            Map<String, Object> metadata = buildChunkMetadata(document, i, chunkPart);
            if (existing == null) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setGroupId(document.getGroupId());
                chunk.setChunkIndex(i);
                chunk.setChunkText(chunkPart.text());
                chunk.setCharStart(chunkPart.start());
                chunk.setCharEnd(chunkPart.end());
                chunk.setMetadataJson(metadata);
                chunk.setChunkHash(sha256(chunkPart.text()));
                chunk.setVectorIndexed(false);
                chunk.setEsIndexed(false);
                documentChunkMapper.insert(chunk);
                result.add(chunk);
            } else {
                existing.setChunkText(chunkPart.text());
                existing.setCharStart(chunkPart.start());
                existing.setCharEnd(chunkPart.end());
                existing.setMetadataJson(metadata);
                String newHash = sha256(chunkPart.text());
                if (!Objects.equals(existing.getChunkHash(), newHash)) {
                    existing.setChunkHash(newHash);
                    existing.setVectorIndexed(false);
                    existing.setEsIndexed(false);
                }
                documentChunkMapper.updateById(existing);
                result.add(existing);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    protected void indexVector(KnowledgeDocument document, List<DocumentChunk> chunks) {
        boolean hasPendingVectorChunk = chunks.stream().anyMatch(chunk -> !Boolean.TRUE.equals(chunk.getVectorIndexed()));
        if (!hasPendingVectorChunk) {
            return;
        }
        vectorStore.delete("documentId == " + document.getId());
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("groupId", document.getGroupId());
            metadata.put("documentId", document.getId());
            metadata.put("chunkId", chunk.getId());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            metadata.put("fileName", document.getFileName());
            metadata.put("charStart", chunk.getCharStart());
            metadata.put("charEnd", chunk.getCharEnd());
            metadata.put("sourceType", document.getFileExt());
            metadata.put("language", "zh");
            metadata.put("hash", chunk.getChunkHash());
            metadata.put("documentTitle", document.getFileName());
            long verEpoch = document.getUpdatedAt() != null
                    ? document.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)
                    : (document.getProcessedAt() != null ? document.getProcessedAt().toEpochSecond(ZoneOffset.UTC) : 0L);
            metadata.put("documentVersionEpoch", verEpoch);
            metadata.put("kbFingerprint", groupKnowledgeRevisionService.fingerprint(document.getGroupId()));
            vectorStore.add(List.of(new Document(chunk.getChunkText(), metadata)));
            chunk.setVectorIndexed(true);
            documentChunkMapper.updateById(chunk);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void indexEs(KnowledgeDocument document, List<DocumentChunk> chunks) {
        ragChunkIndexRepository.deleteByDocumentId(document.getId());
        for (DocumentChunk chunk : chunks) {
            RagChunkIndex index = new RagChunkIndex();
            index.setId(chunk.getId().toString());
            index.setGroupId(document.getGroupId());
            index.setDocumentId(document.getId());
            index.setChunkIndex(chunk.getChunkIndex());
            index.setCharStart(chunk.getCharStart());
            index.setCharEnd(chunk.getCharEnd());
            index.setFileName(document.getFileName());
            index.setContent(chunk.getChunkText());
            ragChunkIndexRepository.save(index);
            chunk.setEsIndexed(true);
            documentChunkMapper.updateById(chunk);
        }
    }

    private void verifyConsistency(KnowledgeDocument document, int expectedChunkSize) {
        long dbChunkCount = documentChunkMapper.selectCount(new QueryWrapper<DocumentChunk>()
                .eq("document_id", document.getId()));
        long vectorIndexedCount = documentChunkMapper.selectCount(new QueryWrapper<DocumentChunk>()
                .eq("document_id", document.getId())
                .eq("vector_indexed", true));
        long esIndexedCount = ragChunkIndexRepository.countByDocumentId(document.getId());
        if (dbChunkCount != expectedChunkSize || vectorIndexedCount != expectedChunkSize || esIndexedCount != expectedChunkSize) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    String.format("一致性校验失败, chunks=%d, vector=%d, es=%d, expected=%d",
                            dbChunkCount, vectorIndexedCount, esIndexedCount, expectedChunkSize));
        }
        HybridRetrievalResultVO retrievalResult = hybridRetrievalService.retrieveWithJudgement(document.getGroupId(), document.getFileName(), 1);
        boolean hasCurrentDocEvidence = retrievalResult.getEvidences().stream()
                .map(ChunkEvidenceVO::getDocumentId)
                .anyMatch(id -> Objects.equals(id, document.getId()));
        if (!hasCurrentDocEvidence) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "检索验收未通过，未召回当前文档");
        }
    }

    private void markDocumentProcessing(Long documentId) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(DOC_PROCESSING);
        update.setFailureReason(null);
        knowledgeDocumentMapper.updateById(update);
    }

    private void markDocumentReady(Long documentId) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(DOC_READY);
        update.setFailureReason(null);
        update.setProcessedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(update);
    }

    private void markDocumentFailure(Long documentId, String reason) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(DOC_FAILED);
        update.setFailureReason(reason == null ? null : (reason.length() > 500 ? reason.substring(0, 500) : reason));
        knowledgeDocumentMapper.updateById(update);
    }

    private String parseDocumentContent(KnowledgeDocument document) {
        byte[] sourceBytes = minioStorageService.download(document.getStorageObjectKey());
        String ext = document.getFileExt() == null ? "" : document.getFileExt().toLowerCase();
        if ("md".equals(ext) || "markdown".equals(ext) || "txt".equals(ext)) {
            return new String(sourceBytes, StandardCharsets.UTF_8);
        }
        try (InputStream inputStream = new ByteArrayInputStream(sourceBytes)) {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档解析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildChunkMetadata(KnowledgeDocument document, int chunkIndex, ChunkPart chunkPart) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileName", document.getFileName());
        metadata.put("groupId", document.getGroupId());
        metadata.put("documentId", document.getId());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("charStart", chunkPart.start());
        metadata.put("charEnd", chunkPart.end());
        metadata.put("sourceType", document.getFileExt());
        metadata.put("language", "zh");
        metadata.put("hash", sha256(chunkPart.text()));
        metadata.put("sectionTitle", "section-" + chunkIndex);
        metadata.put("pageNo", Math.max(1, chunkIndex / 3 + 1));
        metadata.put("documentTitle", document.getFileName());
        long verEpoch = document.getUpdatedAt() != null
                ? document.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)
                : (document.getProcessedAt() != null ? document.getProcessedAt().toEpochSecond(ZoneOffset.UTC) : 0L);
        metadata.put("documentVersionEpoch", verEpoch);
        metadata.put("kbFingerprint", groupKnowledgeRevisionService.fingerprint(document.getGroupId()));
        return metadata;
    }

    private String normalizeContent(String content, String fileExt) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String normalized = content.replace("\u0000", " ").replace("\r\n", "\n").trim();
        String ext = fileExt == null ? "txt" : fileExt.toLowerCase();
        return switch (ext) {
            case "md", "markdown" -> normalized.replaceAll("(?m)^#{1,6}\\s*", "").replace("```", "");
            case "pdf" -> normalized.replaceAll("-\\n", "").replaceAll("\\n{3,}", "\n\n");
            case "doc", "docx" -> normalized.replaceAll("\\n{3,}", "\n\n");
            default -> normalized;
        };
    }

    private void validateContentQuality(String content) {
        if (StrUtil.length(content) < 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档有效内容过短");
        }
        long suspiciousChars = content.chars().filter(c -> c == '�').count();
        double ratio = content.isEmpty() ? 0D : (double) suspiciousChars / content.length();
        if (ratio > 0.1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档疑似乱码，无法入库");
        }
    }

    private List<ChunkPart> splitToChunks(String content, int chunkSize, int overlap) {
        if (StrUtil.isBlank(content)) {
            return List.of();
        }
        String text = content.trim();
        if (text.length() <= chunkSize) {
            return List.of(new ChunkPart(text, 0, text.length()));
        }
        int step = Math.max(1, chunkSize - overlap);
        List<ChunkPart> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(new ChunkPart(text.substring(start, end), start, end));
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
    }

    private String extractFileExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "txt";
        }
        return filename.substring(idx + 1).toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "哈希算法不可用");
        }
    }

    private record ChunkPart(String text, int start, int end) {
    }

    private String defaultContentType(String contentType) {
        return StrUtil.blankToDefault(contentType, "application/octet-stream");
    }

    private String buildStorageKey(Long groupId, String filename) {
        String safeFileName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "group/" + groupId + "/" + UUID.randomUUID() + "-" + safeFileName;
    }

    private void updateDocumentContentHash(Long documentId, String contentHash) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setContentHash(contentHash);
        knowledgeDocumentMapper.updateById(update);
    }
}
