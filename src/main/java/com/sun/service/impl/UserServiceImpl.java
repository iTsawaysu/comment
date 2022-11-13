package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.LoginFormDTO;
import com.sun.dto.Result;
import com.sun.dto.UserDTO;
import com.sun.entity.User;
import com.sun.mapper.UserMapper;
import com.sun.service.UserService;
import com.sun.utils.RegexUtils;
import com.sun.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sun.utils.RedisConstants.*;
import static com.sun.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @author sun
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     */
    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2. 手机号格式正确，则生成验证码
        String captcha = RandomUtil.randomNumbers(6);

        // 3. 将验证码保存到 Redis (login:captcha:phone ---> expire 2 minutes)
        stringRedisTemplate.opsForValue().set(LOGIN_CAPTCHA_KEY + phone, captcha, TTL_TWO, TimeUnit.MINUTES);

        // 4. 发送验证码
        // TODO 暂时不接入第三方短信 API 接口
        log.debug("captcha: {}", captcha);
        return Result.ok();
    }


    /**
     * 短信验证码注册和登录
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 校验手机号
        String loginFormPhone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(loginFormPhone)) {
            return Result.fail("手机号格式错误");
        }

        // 2. 校验验证码
        String captcha = stringRedisTemplate.opsForValue().get(LOGIN_CAPTCHA_KEY + loginFormPhone);
        String loginFormCode = loginForm.getCode();
        if (!StrUtil.equals(captcha, loginFormCode)) {
            return Result.fail("验证码错误");
        }

        // 3. 根据手机号查询用户
        User user = query().eq("phone", loginFormPhone).one();

        // 4. 判断用户是否存在。不存在：数据库中创建用户；存在，保存用户信息到 Redis 中。
        if (user == null) {
            user = createNewUserWithPhone(loginFormPhone);
        }

        // 5. 保存用户信息到 Redis 中（随机生成 Token，作为登录令牌；将 User 对象转为 Hash 存储）
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, fieldValue) -> fieldValue.toString())
        );
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        stringRedisTemplate.expire(LOGIN_CAPTCHA_KEY + token, TTL_THIRTY, TimeUnit.DAYS);
        return Result.ok(token);
    }

    private User createNewUserWithPhone(String phone) {
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        user.setPhone(phone);
        save(user);
        return user;
    }


    @Override
    public Result sign() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期
        LocalDateTime nowDateTime = LocalDateTime.now();
        String formatTime = nowDateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        // 3. 拼接 Key
        String key = USER_SIGN_KEY + userId + formatTime;

        // 4. 获取今天是本月的第几天（1～31，BitMap 则为 0～30）
        int dayOfMonth = nowDateTime.getDayOfMonth();

        // 5. 写入 Redis  SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期
        LocalDateTime nowDateTime = LocalDateTime.now();
        String formatTime = nowDateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        // 3. 拼接 Key
        String key = USER_SIGN_KEY + userId + formatTime;

        // 4. 获取今天是本月的第几天（1～31，BitMap 则为 0～30）
        int dayOfMonth = nowDateTime.getDayOfMonth();

        // 5. 获取本月截止今天的所有签到记录，返回的是一个 十进制数字
        // BITFIELD sign:1010:202210 GET u26 0 （当前为 26 号）
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result.isEmpty() || result == null) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == 0 || num == null) {
            return Result.ok(0);
        }

        // 6. 让这个数字与 1 做 与运算，得到数字的最后一个 Bit 位；判断这个 Bit 位是否为 0。
        int count = 0;
        while (true) {
            // 0：未签到，结束
            if ((num & 1) == 0) {
                break;
            } else {
                // 非0：签到，计数器 +1
                count ++;
            }
            // 右移一位，抛弃最后一个 Bit 位，继续下一个 Bit 位。
            // num = num >> 1;
            num >>>= 1;
        }

        return Result.ok(count);
    }

}
