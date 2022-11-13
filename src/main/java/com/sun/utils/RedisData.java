package com.sun.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author sun
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
