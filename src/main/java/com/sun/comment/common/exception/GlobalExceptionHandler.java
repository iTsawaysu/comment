package com.sun.comment.common.exception;

import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author sun
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public CommonResult<?> businessExceptionHandler(BusinessException e) {
        e.printStackTrace();
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public CommonResult<?> runtimeExceptionHandler(RuntimeException e) {
        e.printStackTrace();
        return CommonResult.error(ErrorCode.SYSTEM_ERROR, e.getMessage());
    }
}
