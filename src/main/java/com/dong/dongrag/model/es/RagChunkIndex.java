package com.dong.dongrag.model.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "rag_chunk_index")
public class RagChunkIndex {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long groupId;

    @Field(type = FieldType.Long)
    private Long documentId;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Integer)
    private Integer charStart;

    @Field(type = FieldType.Integer)
    private Integer charEnd;

    @Field(type = FieldType.Keyword)
    private String fileName;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;
}
