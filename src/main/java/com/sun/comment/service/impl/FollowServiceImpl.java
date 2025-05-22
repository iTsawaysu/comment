package com.sun.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.Follow;
import com.sun.comment.entity.User;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.mapper.FollowMapper;
import com.sun.comment.service.FollowService;
import com.sun.comment.service.UserService;
import com.sun.comment.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author sun
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;

    /**
     * 关注或取关
     * @param followUserId 被关注、取关的用户ID
     * @param isFollowed 是否关注
     */
    @Override
    public CommonResult<String> followOrNot(Long followUserId, Boolean isFollowed) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        // 关注
        if (isFollowed) {
            Follow follow = Follow.builder()
                    .followUserId(followUserId)
                    .userId(userId)
                    .build();
            boolean result = this.save(follow);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // userId 为 key、followUserId 为 value 存入 Redis
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            return CommonResult.success("关注成功");
        // 取关
        } else {
            boolean result = this.remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, followUserId).eq(Follow::getUserId, userId));
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            return CommonResult.success("取消关注成功");
        }
    }

    /**
     * 判断是否关注该用户
     * @param followUserId 被关注用户的ID
     */
    @Override
    public CommonResult<Boolean> isFollowed(Long followUserId) {
        Long count = this.lambdaQuery()
                .eq(Follow::getFollowUserId, followUserId)
                .eq(Follow::getUserId, UserHolder.getUser().getId())
                .count();
        return CommonResult.success(count > 0);
    }

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 被关注用户的ID
     */
    @Override
    public CommonResult<List<UserDTO>> commonFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String selfKey = "follow:" + userId;
        String followKey = "follow:" + followUserId;
        // 获取两个用户之间的交集，即两个用户都关注的用户的 ID
        Set<String> inter = stringRedisTemplate.opsForSet().intersect(selfKey, followKey);
        // 无交集
        if (CollUtil.isEmpty(inter)) {
            return CommonResult.success(Collections.emptyList());
        }
        // 返回交集部分的用户
        List<User> userList = userService.listByIds(inter);
        if (CollUtil.isEmpty(userList)) {
            return CommonResult.success(Collections.emptyList());
        }
        List<UserDTO> userDTOList = userList.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
        return CommonResult.success(userDTOList);
    }
}
