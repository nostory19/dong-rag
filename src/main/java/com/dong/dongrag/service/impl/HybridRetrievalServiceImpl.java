package com.dong.dongrag.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dong.dongrag.config.DongragAiProperties;
import com.dong.dongrag.mapper.DocumentChunkMapper;
import com.dong.dongrag.model.entity.DocumentChunk;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.rag.EvidenceReranker;
import com.dong.dongrag.service.HybridRetrievalService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRetrievalServiceImpl implements HybridRetrievalService {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @Resource
    private DocumentChunkMapper documentChunkMapper;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private DongragAiProperties dongragAiProperties;

    @Resource
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Resource
    private EvidenceReranker evidenceReranker;

    @Override
    public List<ChunkEvidenceVO> hybridRetrieve(Long groupId, String question, int topK, boolean applyRerank) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (dongragAiProperties.isRetrievalCircuitBreakerEnabled()) {
                return circuitBreakerRegistry.circuitBreaker("retrieval")
                        .executeSupplier(() -> doHybridRetrieve(groupId, question, topK, applyRerank));
            }
            return doHybridRetrieve(groupId, question, topK, applyRerank);
        } finally {
            sample.stop(Timer.builder("dongrag.retrieval.hybrid")
                    .description("Hybrid vector+ES retrieval")
                    .tag("apply_rerank", String.valueOf(applyRerank))
                    .register(meterRegistry));
        }
    }

    private List<ChunkEvidenceVO> doHybridRetrieve(Long groupId, String question, int topK, boolean applyRerank) {
        int fetchSize = Math.max(topK * 3, 10);
        List<ChunkEvidenceVO> vectorResults = retrieveFromVector(groupId, question, fetchSize);
        List<ChunkEvidenceVO> esResults = retrieveFromEs(groupId, question, fetchSize);

        Map<String, ChunkEvidenceVO> merged = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();
        int k = 60;
        for (int i = 0; i < vectorResults.size(); i++) {
            ChunkEvidenceVO item = vectorResults.get(i);
            String key = buildKey(item);
            merged.putIfAbsent(key, item);
            rrfScores.merge(key, 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < esResults.size(); i++) {
            ChunkEvidenceVO item = esResults.get(i);
            String key = buildKey(item);
            merged.putIfAbsent(key, item);
            rrfScores.merge(key, 1.0 / (k + i + 1), Double::sum);
        }
        List<ChunkEvidenceVO> mergedRanked = merged.entrySet().stream()
                .map(entry -> {
                    ChunkEvidenceVO vo = entry.getValue();
                    vo.setScore(rrfScores.getOrDefault(entry.getKey(), 0D));
                    vo.setSource("hybrid");
                    return vo;
                })
                .sorted(Comparator.comparing(ChunkEvidenceVO::getScore).reversed())
                .toList();

        int preCap = Math.min(mergedRanked.size(), Math.max(Math.max(fetchSize, dongragAiProperties.getRerankCandidateLimit()), topK * 3));
        List<ChunkEvidenceVO> pre = mergedRanked.stream().limit(preCap).toList();
        List<ChunkEvidenceVO> cores;
        if (applyRerank) {
            cores = evidenceReranker.rerank(question, pre, topK);
        } else {
            cores = pre.stream().limit(topK).toList();
        }
        return withNeighborWindow(groupId, cores, topK);
    }

    @Override
    public HybridRetrievalResultVO retrieveWithJudgement(Long groupId, String question, int topK) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<ChunkEvidenceVO> evidences = hybridRetrieve(groupId, question, topK, true);
            double confidence = evidences.stream()
                    .map(ChunkEvidenceVO::getScore)
                    .filter(java.util.Objects::nonNull)
                    .max(Double::compareTo)
                    .orElse(0D);
            String confidenceLevel = confidence >= 0.03 ? "HIGH" : confidence >= 0.015 ? "MEDIUM" : "LOW";
            boolean evidenceEnough = confidence >= 0.012 && !evidences.isEmpty();

            HybridRetrievalResultVO result = new HybridRetrievalResultVO();
            result.setEvidences(evidences);
            result.setConfidenceScore(confidence);
            result.setConfidenceLevel(confidenceLevel);
            result.setEvidenceEnough(evidenceEnough);
            return result;
        } finally {
            sample.stop(Timer.builder("dongrag.retrieval.judgement")
                    .description("Hybrid retrieval with judgement")
                    .register(meterRegistry));
        }
    }

    private List<ChunkEvidenceVO> retrieveFromVector(Long groupId, String question, int topK) {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(topK).build());
        List<ChunkEvidenceVO> list = new ArrayList<>();
        for (Document doc : hits) {
            Map<String, Object> metadata = doc.getMetadata();
            if (!groupId.toString().equals(String.valueOf(metadata.get("groupId")))) {
                continue;
            }
            ChunkEvidenceVO vo = new ChunkEvidenceVO();
            vo.setDocumentId(Long.valueOf(String.valueOf(metadata.get("documentId"))));
            vo.setChunkIndex(Integer.valueOf(String.valueOf(metadata.get("chunkIndex"))));
            vo.setFileName(String.valueOf(metadata.get("fileName")));
            vo.setCharStart(asInt(metadata.get("charStart")));
            vo.setCharEnd(asInt(metadata.get("charEnd")));
            vo.setContent(doc.getText());
            vo.setSource("vector");
            list.add(vo);
        }
        return list;
    }

    private List<ChunkEvidenceVO> retrieveFromEs(Long groupId, String question, int topK) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(QueryBuilders.bool()
                        .must(QueryBuilders.match().field("content").query(question).build()._toQuery())
                        .filter(QueryBuilders.term().field("groupId").value(groupId).build()._toQuery())
                        .build()._toQuery())
                .withMaxResults(topK)
                .build();
        SearchHits<com.dong.dongrag.model.es.RagChunkIndex> searchHits =
                elasticsearchOperations.search(query, com.dong.dongrag.model.es.RagChunkIndex.class);
        List<ChunkEvidenceVO> list = new ArrayList<>();
        for (SearchHit<com.dong.dongrag.model.es.RagChunkIndex> hit : searchHits) {
            com.dong.dongrag.model.es.RagChunkIndex index = hit.getContent();
            ChunkEvidenceVO vo = new ChunkEvidenceVO();
            vo.setDocumentId(index.getDocumentId());
            vo.setChunkIndex(index.getChunkIndex());
            vo.setFileName(index.getFileName());
            vo.setCharStart(index.getCharStart());
            vo.setCharEnd(index.getCharEnd());
            vo.setContent(index.getContent());
            vo.setScore((double) hit.getScore());
            vo.setSource("keyword");
            list.add(vo);
        }
        return list;
    }

    private String buildKey(ChunkEvidenceVO item) {
        return item.getDocumentId() + "_" + item.getChunkIndex();
    }

    private List<ChunkEvidenceVO> withNeighborWindow(Long groupId, List<ChunkEvidenceVO> ranked, int topK) {
        Map<String, ChunkEvidenceVO> expanded = new HashMap<>();
        for (ChunkEvidenceVO core : ranked) {
            expanded.putIfAbsent(buildKey(core), core);
            List<Integer> neighbors = List.of(core.getChunkIndex() - 1, core.getChunkIndex() + 1);
            for (Integer neighborIndex : neighbors) {
                if (neighborIndex < 0) {
                    continue;
                }
                DocumentChunk neighbor = documentChunkMapper.selectOne(new QueryWrapper<DocumentChunk>()
                        .eq("group_id", groupId)
                        .eq("document_id", core.getDocumentId())
                        .eq("chunk_index", neighborIndex)
                        .last("limit 1"));
                if (neighbor == null) {
                    continue;
                }
                ChunkEvidenceVO vo = new ChunkEvidenceVO();
                vo.setDocumentId(neighbor.getDocumentId());
                vo.setChunkIndex(neighbor.getChunkIndex());
                vo.setFileName(core.getFileName());
                vo.setCharStart(neighbor.getCharStart());
                vo.setCharEnd(neighbor.getCharEnd());
                vo.setContent(neighbor.getChunkText());
                vo.setScore((core.getScore() == null ? 0D : core.getScore()) * 0.85);
                vo.setSource("window");
                expanded.putIfAbsent(buildKey(vo), vo);
            }
        }
        return expanded.values().stream()
                .sorted(Comparator.comparing(ChunkEvidenceVO::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(Math.max(topK, Math.min(topK * 2, 12)))
                .toList();
    }

    private Integer asInt(Object val) {
        if (val == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(val));
    }
}
