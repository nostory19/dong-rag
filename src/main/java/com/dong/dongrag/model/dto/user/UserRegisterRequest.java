package com.dong.dongrag.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {

    private String userCode;

    private String displayName;

    private String userPassword;

    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
