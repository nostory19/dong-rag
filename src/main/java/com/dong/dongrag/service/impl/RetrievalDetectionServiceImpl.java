package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dong.dongrag.config.DongragAiProperties;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.model.dto.retrieval.RetrievalDetectCase;
import com.dong.dongrag.model.dto.retrieval.RetrievalDetectRequest;
import com.dong.dongrag.model.vo.ChunkEvidenceVO;
import com.dong.dongrag.model.vo.HybridRetrievalResultVO;
import com.dong.dongrag.model.vo.RetrievalDetectCaseResultVO;
import com.dong.dongrag.model.vo.RetrievalDetectResponseVO;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupService;
import com.dong.dongrag.service.HybridRetrievalService;
import com.dong.dongrag.service.RetrievalDetectionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetrievalDetectionServiceImpl implements RetrievalDetectionService {

    private static final int MAX_CONTENT_PREVIEW = 500;

    @Resource
    private AuthContextService authContextService;

    @Resource
    private GroupService groupService;

    @Resource
    private HybridRetrievalService hybridRetrievalService;

    @Resource
    private DongragAiProperties dongragAiProperties;

    @Override
    public RetrievalDetectResponseVO detect(RetrievalDetectRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "groupId 不能为空");
        }
        if (request.getCases() == null || request.getCases().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cases 不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        int topK = request.getTopK() == null ? 5 : Math.min(10, Math.max(1, request.getTopK()));
        boolean compareRerank = Boolean.TRUE.equals(request.getIncludeRerankComparison())
                && dongragAiProperties.isRetrievalRerankEnabled();

        List<RetrievalDetectCaseResultVO> details = new ArrayList<>();
        int labeled = 0;
        double sumHit1 = 0;
        double sumHitK = 0;
        double sumRr = 0;
        double sumHit1Base = 0;
        double sumHitKBase = 0;
        double sumRrBase = 0;
        int labeledBase = 0;

        for (RetrievalDetectCase c : request.getCases()) {
            if (c == null || StrUtil.isBlank(c.getQuestion())) {
                details.add(RetrievalDetectCaseResultVO.builder()
                        .question(c == null ? "" : c.getQuestion())
                        .labeled(false)
                        .error("问题为空，已跳过")
                        .evidences(List.of())
                        .build());
                continue;
            }
            boolean goldOk = c.getGoldDocumentId() != null && c.getGoldChunkIndex() != null;
            try {
                HybridRetrievalResultVO judged = hybridRetrievalService.retrieveWithJudgement(
                        request.getGroupId(), c.getQuestion().trim(), topK);
                List<ChunkEvidenceVO> ranked = hybridRetrievalService.hybridRetrieve(
                        request.getGroupId(), c.getQuestion().trim(), topK, true);

                Integer rankBaseline = null;
                if (compareRerank && goldOk) {
                    List<ChunkEvidenceVO> baseList = hybridRetrievalService.hybridRetrieve(
                            request.getGroupId(), c.getQuestion().trim(), topK, false);
                    rankBaseline = findGoldRank(baseList, c.getGoldDocumentId(), c.getGoldChunkIndex());
                }

                Integer rank = null;
                if (goldOk) {
                    rank = findGoldRank(ranked, c.getGoldDocumentId(), c.getGoldChunkIndex());
                    labeled++;
                    boolean h1 = rank != null && rank == 1;
                    boolean hk = rank != null && rank <= topK;
                    double rr = rank != null ? 1.0 / rank : 0D;
                    sumHit1 += h1 ? 1D : 0D;
                    sumHitK += hk ? 1D : 0D;
                    sumRr += rr;

                    Boolean hkBase = null;
                    Double rrBase = null;
                    if (compareRerank) {
                        labeledBase++;
                        boolean h1b = rankBaseline != null && rankBaseline == 1;
                        hkBase = rankBaseline != null && rankBaseline <= topK;
                        rrBase = rankBaseline != null ? 1.0 / rankBaseline : 0D;
                        sumHit1Base += h1b ? 1D : 0D;
                        sumHitKBase += hkBase ? 1D : 0D;
                        sumRrBase += rrBase;
                    }

                    details.add(RetrievalDetectCaseResultVO.builder()
                            .question(c.getQuestion().trim())
                            .confidenceScore(judged.getConfidenceScore())
                            .confidenceLevel(judged.getConfidenceLevel())
                            .evidenceEnough(judged.isEvidenceEnough())
                            .rankOfGold(rank)
                            .hitAt1(rank != null && rank == 1)
                            .hitAtK(hk)
                            .reciprocalRank(rr)
                            .rankOfGoldBaseline(rankBaseline)
                            .hitAtKBaseline(hkBase)
                            .reciprocalRankBaseline(rrBase)
                            .goldDocumentId(c.getGoldDocumentId())
                            .goldChunkIndex(c.getGoldChunkIndex())
                            .labeled(true)
                            .evidences(truncateEvidences(ranked))
                            .build());
                } else {
                    details.add(RetrievalDetectCaseResultVO.builder()
                            .question(c.getQuestion().trim())
                            .confidenceScore(judged.getConfidenceScore())
                            .confidenceLevel(judged.getConfidenceLevel())
                            .evidenceEnough(judged.isEvidenceEnough())
                            .rankOfGold(null)
                            .hitAt1(null)
                            .hitAtK(null)
                            .reciprocalRank(null)
                            .goldDocumentId(c.getGoldDocumentId())
                            .goldChunkIndex(c.getGoldChunkIndex())
                            .labeled(false)
                            .evidences(truncateEvidences(ranked))
                            .build());
                }
            } catch (Exception e) {
                details.add(RetrievalDetectCaseResultVO.builder()
                        .question(c.getQuestion().trim())
                        .labeled(goldOk)
                        .goldDocumentId(c.getGoldDocumentId())
                        .goldChunkIndex(c.getGoldChunkIndex())
                        .rankOfGold(null)
                        .hitAt1(goldOk ? Boolean.FALSE : null)
                        .hitAtK(goldOk ? Boolean.FALSE : null)
                        .reciprocalRank(goldOk ? 0D : null)
                        .error(e.getMessage())
                        .evidences(List.of())
                        .build());
                if (goldOk) {
                    labeled++;
                }
            }
        }

        Double meanHit1 = labeled > 0 ? sumHit1 / labeled : null;
        Double meanHitK = labeled > 0 ? sumHitK / labeled : null;
        Double mrr = labeled > 0 ? sumRr / labeled : null;
        Double meanHit1Baseline = compareRerank && labeledBase > 0 ? sumHit1Base / labeledBase : null;
        Double meanHitKBaseline = compareRerank && labeledBase > 0 ? sumHitKBase / labeledBase : null;
        Double mrrBaseline = compareRerank && labeledBase > 0 ? sumRrBase / labeledBase : null;

        return RetrievalDetectResponseVO.builder()
                .caseCount(details.size())
                .labeledCount(labeled)
                .meanHitAt1(meanHit1)
                .meanHitAtK(meanHitK)
                .mrr(mrr)
                .meanHitAt1Baseline(meanHit1Baseline)
                .meanHitAtKBaseline(meanHitKBaseline)
                .mrrBaseline(mrrBaseline)
                .details(details)
                .build();
    }

    private static Integer findGoldRank(List<ChunkEvidenceVO> ranked, Long goldDocId, Integer goldChunk) {
        if (goldChunk == null) {
            return null;
        }
        for (int i = 0; i < ranked.size(); i++) {
            ChunkEvidenceVO e = ranked.get(i);
            if (e.getDocumentId() != null && e.getChunkIndex() != null
                    && e.getDocumentId().equals(goldDocId) && e.getChunkIndex().equals(goldChunk)) {
                return i + 1;
            }
        }
        return null;
    }

    private static List<ChunkEvidenceVO> truncateEvidences(List<ChunkEvidenceVO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ChunkEvidenceVO> out = new ArrayList<>(source.size());
        for (ChunkEvidenceVO e : source) {
            ChunkEvidenceVO copy = new ChunkEvidenceVO();
            copy.setDocumentId(e.getDocumentId());
            copy.setChunkIndex(e.getChunkIndex());
            copy.setFileName(e.getFileName());
            copy.setCharStart(e.getCharStart());
            copy.setCharEnd(e.getCharEnd());
            copy.setScore(e.getScore());
            copy.setSource(e.getSource());
            String text = e.getContent();
            if (text != null && text.length() > MAX_CONTENT_PREVIEW) {
                copy.setContent(text.substring(0, MAX_CONTENT_PREVIEW) + "…");
            } else {
                copy.setContent(text);
            }
            out.add(copy);
        }
        return out;
    }
}
