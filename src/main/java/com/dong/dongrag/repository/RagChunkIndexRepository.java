package com.dong.dongrag.repository;

import com.dong.dongrag.model.es.RagChunkIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface RagChunkIndexRepository extends ElasticsearchRepository<RagChunkIndex, String> {

    void deleteByDocumentId(Long documentId);

    long countByDocumentId(Long documentId);
}
