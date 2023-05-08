package com.sun.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author sun
 */
@Data
public class UserDTO implements Serializable {
    public static final long serialVersionUID = 1L;

    private Long id;

    private String nickName;

    private String icon;

}
