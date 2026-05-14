package com.dong.dongrag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dong.dongrag.mapper.KnowledgeDocumentMapper;
import com.dong.dongrag.model.entity.KnowledgeDocument;
import com.dong.dongrag.service.GroupKnowledgeRevisionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Service
public class GroupKnowledgeRevisionServiceImpl implements GroupKnowledgeRevisionService {

    private static final String READY = "READY";

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public String fingerprint(Long groupId) {
        if (groupId == null) {
            return "0";
        }
        long count = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getGroupId, groupId)
                .eq(KnowledgeDocument::getStatus, READY)
                .eq(KnowledgeDocument::getDeleted, false));
        KnowledgeDocument latest = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getGroupId, groupId)
                .eq(KnowledgeDocument::getStatus, READY)
                .eq(KnowledgeDocument::getDeleted, false)
                .orderByDesc(KnowledgeDocument::getUpdatedAt)
                .last("limit 1"));
        long ts = latest == null || latest.getUpdatedAt() == null
                ? 0L
                : latest.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);
        return count + ":" + ts;
    }
}
