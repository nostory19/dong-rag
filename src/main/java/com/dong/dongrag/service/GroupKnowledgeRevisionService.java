package com.dong.dongrag.service;

/**
 * 组内知识库版本指纹，用于缓存失效（READY 文档数量 + 最近更新时间）。
 */
public interface GroupKnowledgeRevisionService {

    /**
     * 稳定字符串，随组内 READY 文档集合变化而变化。
     */
    String fingerprint(Long groupId);
}
