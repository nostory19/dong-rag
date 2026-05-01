package com.dong.dongrag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type=IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_code")
    private String userCode;

    @TableField("display_name")
    private String displayName;

    @TableField("user_password")
    private String userPassword;

    @TableField("user_role")
    private String userRole;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
