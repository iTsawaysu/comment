package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.dto.LoginFormDTO;
import com.sun.dto.Result;
import com.sun.entity.User;

/**
 * @author sun
 */
public interface UserService extends IService<User> {

    /**
     * 发送验证码
     */
    Result sendCode(String phone);

    /**
     * 短信验证码注册和登录
     */
    Result login(LoginFormDTO loginForm);

    Result sign();

    Result signCount();

}
