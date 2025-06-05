package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.Result;
import com.sun.comment.entity.dto.LoginFormDTO;
import com.sun.comment.entity.User;

/**
 * @author sun
 */
public interface UserService extends IService<User> {

    /**
     * 发送手机验证码并将验证码保存到 Redis 中
     */
    Result<String> sendCode(String phone);

    /**
     * 登录功能（登录成功后返回 Token）
     *
     * @param loginForm 登录参数：手机号、验证码（验证码登录）；或者手机号、密码（密码登录）。
     */
    Result<String> login(LoginFormDTO loginForm);

    /**
     * 签到
     */
    Result<String> sign();

    /**
     * 统计本月当前用户截止当前时间连续签到的天数
     */
    Result<Integer> serialSignCount4CurrentMonth();
}
