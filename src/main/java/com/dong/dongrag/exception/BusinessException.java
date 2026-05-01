package com.dong.dongrag.exception;

import lombok.Getter;

/**
 * @author by hongdou
 * @date 2025/12/14.
 * @DESC: 自定义业务异常类
 */

@Getter
public class BusinessException extends RuntimeException {
    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String message) {
        // 调用父类的构造方法，设置异常信息
        super(message);
        this.code = code;
    }

    /**
     * 构造方法，使用ErrorCode枚举类的状态码和错误信息
     *
     * @param errorCode
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 构造方法，使用ErrorCode枚举类的状态码和自定义错误信息
     *
     * @param errorCode
     * @param message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    // 使用方式是：
    // throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
}
