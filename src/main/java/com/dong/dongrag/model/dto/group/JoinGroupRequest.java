package com.dong.dongrag.model.dto.group;

import lombok.Data;

import java.io.Serializable;

@Data
public class JoinGroupRequest implements Serializable {

    private Long groupId;

    private static final long serialVersionUID = 1L;
}
