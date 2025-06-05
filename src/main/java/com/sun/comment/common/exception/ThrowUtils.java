package com.sun.comment.common.exception;

import com.sun.comment.common.ReturnCode;

/**
 * @author sun
 */
public class ThrowUtils {
    /**
     * 条件成立时抛出指定的运行时异常
     *
     * @param condition        判断条件
     * @param runtimeException 需要抛出的异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    public static void throwIf(boolean condition, ReturnCode returnCode) {
        throwIf(condition, new BusinessException(returnCode));
    }

    public static void throwIf(boolean condition, ReturnCode returnCode, String message) {
        throwIf(condition, new BusinessException(returnCode, message));
    }
}
