package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.Result;
import com.sun.dto.UserDTO;
import com.sun.entity.Follow;
import com.sun.mapper.FollowMapper;
import com.sun.service.FollowService;
import com.sun.service.UserService;
import com.sun.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author sun
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Override
    public Result followOrNot(Long followUserId, Boolean isFollowed) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;

        // 判断是关注还是取关
        if (BooleanUtil.isTrue(isFollowed)) {
            // 关注，增加
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSucceed = save(follow);
            if (BooleanUtil.isTrue(isSucceed)) {
                // 添加到 Redis 中（当前用户ID 为 key，关注用户ID 为 value）
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关，删除
            boolean isSucceed = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId));
            if (BooleanUtil.isTrue(isSucceed)) {
                // 从 Redis 中删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = lambdaQuery().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String selfKey = "follow:" + userId;
        String aimKey = "follow:" + followUserId;
        Set<String> userIdSet = stringRedisTemplate.opsForSet().intersect(selfKey, aimKey);
        if (userIdSet.isEmpty() || userIdSet == null) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTOList = userService.listByIds(userIdSet)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
