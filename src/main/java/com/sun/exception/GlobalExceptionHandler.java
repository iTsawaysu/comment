package com.sun.exception;

import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
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
        log.error("BusinessException", e);
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public CommonResult<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return CommonResult.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
