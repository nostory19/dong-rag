package com.dong.dongrag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assistant_conversations")
public class AssistantConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("group_id")
    private Long groupId;

    @TableField("template_id")
    private String templateId;

    @TableField("title")
    private String title;

    @TableField("rolling_summary")
    private String rollingSummary;

    @TableField("slot_state_json")
    private String slotStateJson;

    @TableField("last_compressed_at")
    private LocalDateTime lastCompressedAt;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
