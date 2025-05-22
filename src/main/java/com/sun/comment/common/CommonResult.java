package com.sun.comment.common;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author sun
 */
@Data
public class CommonResult<T> implements Serializable {
    private Boolean success;
    private int code;
    private T data;
    private String message;

    public CommonResult(Boolean success, int code, T data, String message) {
        this.success = success;
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(true, 0, data, "ok");
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        return new CommonResult<>(false, errorCode.getCode(), null, errorCode.getMessage());
    }

    public static <T> CommonResult<T> error(int code, String message) {
        return new CommonResult<>(false, code, null, message);
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode, String message) {
        return new CommonResult<>(false, errorCode.getCode(), null, message);
    }
}
