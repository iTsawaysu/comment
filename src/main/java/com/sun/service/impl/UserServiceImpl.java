package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.dto.LoginFormDTO;
import com.sun.dto.UserDTO;
import com.sun.entity.User;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.UserMapper;
import com.sun.service.UserService;
import com.sun.utils.RegexUtils;
import com.sun.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

import static com.sun.common.RedisConstants.*;
import static com.sun.common.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @author sun
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码并将验证码保存到 Redis 中
     */
    @Override
    public CommonResult<String> sendCode(String phone) {
        // 1. 验证手机号
        ThrowUtils.throwIf(StringUtils.isBlank(phone), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(Boolean.TRUE.equals(RegexUtils.isPhoneInvalid(phone)), ErrorCode.PARAMS_ERROR, "该手机号不合法");

        // 2. 生成验证码并存入 Redis（设置过期时间为 2 min）
        String captcha = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CAPTCHA_KEY + phone, captcha, TTL_TWO, TimeUnit.MINUTES);

        // 3. 发送验证码
        // todo 暂时不接入第三方短信 API 接口
        log.info("captcha = {}", captcha);

        return CommonResult.success("验证码发送成功");
    }

    /**
     * 登录功能（登录成功后返回 Token）
     *
     * @param loginForm 登录请求的参数：手机号、验证码（验证码登录）；或者手机号、密码（密码登录）。
     */
    @Override
    public CommonResult<String> login(LoginFormDTO loginForm) {
        // 1. 校验请求参数
        String loginPhone = loginForm.getPhone();
        String loginCaptcha = loginForm.getCode();
        ThrowUtils.throwIf(StringUtils.isAnyBlank(loginPhone, loginCaptcha), ErrorCode.PARAMS_ERROR, "手机号和验证码不能为空");
        ThrowUtils.throwIf(Boolean.TRUE.equals(RegexUtils.isPhoneInvalid(loginPhone)), ErrorCode.PARAMS_ERROR, "该手机号不合法");

        // 2. 从 Redis 中获取该手机号对应的验证码，并进行比对
        String captcha = stringRedisTemplate.opsForValue().get(LOGIN_CAPTCHA_KEY + loginPhone);
        ThrowUtils.throwIf(!StringUtils.equals(loginCaptcha, captcha), ErrorCode.PARAMS_ERROR, "验证码错误");

        // 3. 判断当前手机号是否已注册（未注册则创建新用户）
        User user = this.lambdaQuery().eq(User::getPhone, loginPhone).one();
        if (user == null) {
            user = createNewUser(user, loginPhone);
        }

        // 4. 避免敏感信息泄漏，用一个 UserDTO 装载必要信息即可
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 5. 随机生成 Token 作为登录令牌
        String token = UUID.randomUUID().toString(true);
        String loginUserKey = LOGIN_USER_KEY + token;

        // 6. 保存用户信息到 Redis 中并设置有效时间。（使用 Hash 存储 User 对象）
        Map<String, Object> map4User = BeanUtil.beanToMap(userDTO, new HashMap<>(16),
                CopyOptions.create()
                        // 忽略空值，当源对象为 null 时，不注入此值
                        .ignoreNullValue()
                        // StringRedisTemplate 只支持 String 类型，将属性转换为 String 后再存储到 Map 中（此处也需要判空）
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return "";
                            }
                            return fieldValue.toString();
                        })
        );
        stringRedisTemplate.opsForHash().putAll(loginUserKey, map4User);
        stringRedisTemplate.expire(loginUserKey, TTL_THIRTY, TimeUnit.MINUTES);
        return CommonResult.success(token);
    }

    /**
     * 根据手机号创建用户
     */
    private User createNewUser(User user, String loginPhone) {
        user = new User();
        user.setPhone(loginPhone);
        // 为 Nickname 设置前缀（USER_NICK_NAME_PREFIX = "user_"）
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        boolean result = this.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user;
    }

    /**
     * 签到
     */
    @Override
    public CommonResult<String> sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = DateTimeFormatter.ofPattern(":yyyyMM").format(now);
        // sign:1:202305
        String key = USER_SIGN_KEY + userId + date;
        int dayOfMonth = now.getDayOfMonth();

        // Key - sign:1:202305（用户每个月的签到信息）、offset - 当月的哪一天（哪一个 BIT 位）、Value - 1 / 0。
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return CommonResult.success("签到成功");
    }

    /**
     * 统计本月当前用户截止当前时间连续签到的天数
     */
    @Override
    public CommonResult<Integer> serialSignCount4CurrentMonth() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = DateTimeFormatter.ofPattern(":yyyyMM").format(now);
        String key = USER_SIGN_KEY + userId + date;

        // 本月截止当前的签到记录，返回的是一个十进制数字。（当前是本月的第几天，就查询几个 BIT 位）
        int dayOfMonth = now.getDayOfMonth();
        List<Long> signCount = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        // 没有任何结果
        if (CollectionUtil.isEmpty(signCount)) {
            return CommonResult.success(0);
        }

        // List 中只有一条数据，直接取出作为结果
        Long num = signCount.get(0);
        if (num == 0 || null == num) {
            return CommonResult.success(0);
        }

        // 与 1 进行与运算，每与一次就将签到结果右移一位，实现遍历。
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            // 右移一位，抛弃最后一个 Bit 位，继续下一个 Bit 位。
            num = num >> 1;
        }
        return CommonResult.success(count);
    }
}
