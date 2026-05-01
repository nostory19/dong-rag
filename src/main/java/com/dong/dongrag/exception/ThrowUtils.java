package com.dong.dongrag.exception;

/**
 * @author by hongdou
 * @date 2025/12/14.
 * @DESC: 抛出异常工具类
 * 提供了抛出异常的静态方法，根据条件判断是否抛出异常
 */
public class ThrowUtils {

    /**
     * 当条件为true时，抛出指定的运行时异常
     *
     * @param condition        条件表达式
     * @param runtimeException 要抛出的运行时异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }


    /**
     * 当条件为true时，抛出指定的错误码异常
     *
     * @param condition
     * @param errorCode
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 当条件为true时，抛出指定的错误码异常，异常信息为自定义消息
     *
     * @param condition
     * @param errorCode
     * @param message
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}
