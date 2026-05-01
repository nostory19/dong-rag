package com.dong.dongrag.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginUserVO implements Serializable {

    private Long id;

    private String userCode;

    private String displayName;

    private String userRole;

    private String token;

    private static final long serialVersionUID = 1L;
}
