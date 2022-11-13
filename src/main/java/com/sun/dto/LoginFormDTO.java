package com.sun.dto;

import lombok.Data;

/**
 * @author sun
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
