package com.dong.dongrag.common;

import com.dong.dongrag.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author by hongdou
 * @date 2025/12/14.
 * @DESC: 基础响应类
 */

@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    /**
     * 成功响应
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
