package com.sun.comment.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sun.comment.common.RedisConstants.LOGIN_USER_KEY;
import static com.sun.comment.common.RedisConstants.TTL_THIRTY;

/**
 * @author sun
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取 Token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 直接放行
            return true;
        }
        // 2. 根据 Token 获取 Redis 中存储的用户信息
        String loginUserKey = LOGIN_USER_KEY + token;
        // entries(key)：返回 key 对应的所有 Map 键值对
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(loginUserKey);
        if (MapUtil.isEmpty(map)) {
            // 直接放行
            return true;
        }
        // 3. 将 Map 转换为 UserDTO（第三个参数 isIgnoreError：是否忽略注入错误）后，存入 ThreadLocal
        UserDTO userDTO = new UserDTO();
        userDTO = BeanUtil.fillBeanWithMap(map, userDTO, false);
        UserHolder.saveUser(userDTO);
        // 4. 刷新 Token 有效期
        stringRedisTemplate.expire(loginUserKey, TTL_THIRTY, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 线程处理完之后移除用户，防止内存泄漏
        UserHolder.removeUser();
    }
}
