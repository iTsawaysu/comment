package com.sun.controller;


import cn.hutool.core.bean.BeanUtil;
import com.sun.common.CommonResult;
import com.sun.dto.LoginFormDTO;
import com.sun.dto.UserDTO;
import com.sun.entity.User;
import com.sun.entity.UserInfo;
import com.sun.service.UserInfoService;
import com.sun.service.UserService;
import com.sun.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * @author sun
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;

    /**
     * 发送手机验证码并将验证码保存到 Redis 中
     */
    @PostMapping("/code")
    public CommonResult<String> sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 登录功能（登录成功后返回 Token）
     * @param loginForm 登录请求的参数：手机号、验证码（验证码登录）；或者手机号、密码（密码登录）。
     */
    @PostMapping("/login")
    public CommonResult<String> login(@RequestBody LoginFormDTO loginForm){
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     */
    @PostMapping("/logout")
    public CommonResult<String> logout(){
        return CommonResult.success("成功退出");
    }

    /**
     * 获取当前登录的用户并返回
     */
    @GetMapping("/me")
    public CommonResult<UserDTO> me(){
        return CommonResult.success(UserHolder.getUser());
    }

    /**
     * 根据用户 id 查看用户详情
     */
    @GetMapping("/info/{id}")
    public CommonResult<UserInfo> info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return CommonResult.success(null);
        }
        info.setCreateTime(LocalDateTime.now());
        info.setUpdateTime(LocalDateTime.now());
        // 返回
        return CommonResult.success(info);
    }

    /**
     * 根据 id 查询用户
     */
    @GetMapping("/{id}")
    public CommonResult<UserDTO> getUserById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return CommonResult.success(null);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return CommonResult.success(userDTO);
    }

    /**
     * 签到
     */
    @PostMapping("/sign")
    public CommonResult<String> sign() {
        return userService.sign();
    }

    /**
     * 统计本月当前用户截止当前时间连续签到的天数
     */
    @GetMapping("/sign/count")
    public CommonResult<Integer> serialSignCount4CurrentMonth() {
        return userService.serialSignCount4CurrentMonth();
    }
}
