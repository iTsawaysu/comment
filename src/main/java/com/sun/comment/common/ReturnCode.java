package com.sun.comment.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author sun
 */
@Getter
@AllArgsConstructor
public enum ReturnCode {
    SUCCESS(200, "ok"),

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED_ERROR(401, "未认证"),
    FORBIDDEN_ERROR(403, "未授权"),
    NOT_FOUND_ERROR(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    SYSTEM_ERROR(500, "系统错误"),
    OPERATION_ERROR(501, "操作失败"),
    DATA_ALREADY_EXISTS(502, "数据已存在"),
    SERVICE_UNAVAILABLE(503, "服务不可用");

    private final int code;
    private final String message;
}
