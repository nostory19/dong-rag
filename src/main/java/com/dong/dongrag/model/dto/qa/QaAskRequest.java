package com.dong.dongrag.model.dto.qa;

import lombok.Data;

import java.io.Serializable;

@Data
public class QaAskRequest implements Serializable {

    private Long groupId;

    private String question;

    private Integer topK = 5;

    private static final long serialVersionUID = 1L;
}
