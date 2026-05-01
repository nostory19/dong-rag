package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupVO implements Serializable {

    private Long id;

    private String groupCode;

    private String groupName;

    private String status;

    private static final long serialVersionUID = 1L;
}
