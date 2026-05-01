package com.dong.dongrag.service;

import com.dong.dongrag.model.dto.document.IngestTextRequest;
import com.dong.dongrag.model.vo.IngestionTaskVO;
import org.springframework.web.multipart.MultipartFile;

public interface RagIngestionService {

    IngestionTaskVO ingestPlainText(IngestTextRequest request);

    IngestionTaskVO ingestFile(Long groupId, MultipartFile file);

    IngestionTaskVO getTaskStatus(Long jobId);

    void processIngestionJob(Long jobId);

    void rebuildDocumentIndexes(Long documentId);
}
