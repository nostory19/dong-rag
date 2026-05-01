package com.dong.dongrag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("documents")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private Long groupId;

    @TableField("uploader_user_id")
    private Long uploaderUserId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_ext")
    private String fileExt;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("storage_bucket")
    private String storageBucket;

    @TableField("storage_object_key")
    private String storageObjectKey;

    @TableField("status")
    private String status;

    @TableField("content_hash")
    private String contentHash;

    @TableField("deleted")
    private Boolean deleted;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    @TableField("processed_at")
    private LocalDateTime processedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
