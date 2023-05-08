package com.sun.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author sun
 */
@Data
public class RedisData implements Serializable {
    public static final long serialVersionUID = 1L;

    private LocalDateTime expireTime;

    private Object data;
}
