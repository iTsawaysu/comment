package com.sun.dto;

import lombok.Data;

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
