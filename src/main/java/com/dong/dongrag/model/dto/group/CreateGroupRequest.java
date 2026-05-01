package com.dong.dongrag.model.dto.group;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateGroupRequest implements Serializable {

    private String groupCode;

    private String groupName;

    private static final long serialVersionUID = 1L;
}
