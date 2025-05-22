package com.sun.comment.entity.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RedisData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private LocalDateTime expireTime;
    private Object data;
}
