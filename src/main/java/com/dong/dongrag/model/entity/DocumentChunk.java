package com.dong.dongrag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dong.dongrag.config.typehandler.JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "document_chunks", autoResultMap = true)
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("group_id")
    private Long groupId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("chunk_text")
    private String chunkText;

    @TableField("chunk_summary")
    private String chunkSummary;

    @TableField("char_start")
    private Integer charStart;

    @TableField("char_end")
    private Integer charEnd;

    @TableField(value = "metadata_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> metadataJson;

    @TableField("chunk_hash")
    private String chunkHash;

    @TableField("vector_indexed")
    private Boolean vectorIndexed;

    @TableField("es_indexed")
    private Boolean esIndexed;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
