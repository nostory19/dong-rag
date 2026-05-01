package com.dong.dongrag.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {

    private String userCode;

    private String userPassword;

    private static final long serialVersionUID = 1L;
}
