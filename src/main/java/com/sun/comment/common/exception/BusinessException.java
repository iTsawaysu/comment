package com.sun.comment.common.exception;

import com.sun.comment.common.ReturnCode;
import lombok.Getter;

/**
 * @author sun
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ReturnCode returnCode) {
        super(returnCode.getMessage());
        this.code = returnCode.getCode();
    }

    public BusinessException(ReturnCode returnCode, String message) {
        super(message);
        this.code = returnCode.getCode();
    }
}
