package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.dto.LoginFormDTO;
import com.sun.comment.entity.User;
import com.sun.comment.mapper.UserMapper;
import com.sun.comment.service.UserService;
import org.springframework.stereotype.Service;

/**
 * @author sun
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 发送手机验证码并将验证码保存到 Redis 中
     */
    @Override
    public Result<String> sendCode(String phone) {
        return null;
    }

    /**
     * 登录功能（登录成功后返回 Token）
     *
     * @param loginForm 登录参数：手机号、验证码（验证码登录）；或者手机号、密码（密码登录）。
     */
    @Override
    public Result<String> login(LoginFormDTO loginForm) {
        return null;
    }

    /**
     * 签到
     */
    @Override
    public Result<String> sign() {
        return null;
    }

    /**
     * 统计本月当前用户截止当前时间连续签到的天数
     */
    @Override
    public Result<Integer> serialSignCount4CurrentMonth() {
        return null;
    }
}
