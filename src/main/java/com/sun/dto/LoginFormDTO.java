package com.sun.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author sun
 */
@Data
public class LoginFormDTO implements Serializable {
    public static final long serialVersionUID = 1L;

    private String phone;

    private String code;

    private String password;

}
