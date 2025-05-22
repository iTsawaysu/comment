package com.sun.comment.entity.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author sun
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
