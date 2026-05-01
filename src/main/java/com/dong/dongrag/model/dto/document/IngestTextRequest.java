package com.dong.dongrag.model.dto.document;

import lombok.Data;

import java.io.Serializable;

@Data
public class IngestTextRequest implements Serializable {

    private Long groupId;

    private String fileName;

    private String content;

    private static final long serialVersionUID = 1L;
}
