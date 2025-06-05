package com.sun.comment.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author sun
 */
@Data
@AllArgsConstructor
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(ReturnCode.SUCCESS.getCode(), ReturnCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ReturnCode.SUCCESS.getCode(), ReturnCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ReturnCode returnCode) {
        return new Result<>(returnCode.getCode(), returnCode.getMessage(), null);
    }

    public static <T> Result<T> error(ReturnCode returnCode, String message) {
        return new Result<>(returnCode.getCode(), message, null);
    }

    public static <T> Result<T> error(ReturnCode returnCode, T data) {
        return new Result<>(returnCode.getCode(), returnCode.getMessage(), data);
    }
}
