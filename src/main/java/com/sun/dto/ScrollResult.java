package com.sun.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author sun
 */
@Data
public class ScrollResult implements Serializable {
    public static final long serialVersionUID = 1L;

    private List<?> list;

    private Long minTime;

    private Integer offset;
}
