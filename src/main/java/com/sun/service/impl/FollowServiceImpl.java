package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.dto.UserDTO;
import com.sun.entity.Follow;
import com.sun.entity.User;
import com.sun.exception.ThrowUtils;
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

    /**
     * 关注或取关
     * @param followUserId 关注、取关的用户ID
     * @param isFollowed   是否关注
     */
    @Override
    public CommonResult<String> followOrNot(Long followUserId, Boolean isFollowed) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        boolean result = false;
        if (BooleanUtil.isTrue(isFollowed)) {
            // 关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            result = this.save(follow);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

            // userId 为 Key、followUserId 为 Value 存入 Redis
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            return CommonResult.success("关注成功");
        } else {
            // 取关
            result = this.remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, followUserId).eq(Follow::getUserId, userId));
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

            // 从 Redis 中删除
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            return CommonResult.success("取消关注成功");
        }
    }

    /**
     * 判断是否关注该用户
     * @param followUserId 关注用户的ID
     */
    @Override
    public CommonResult<Boolean> isFollowed(Long followUserId) {
        Integer count = this.lambdaQuery().eq(Follow::getFollowUserId, followUserId).eq(Follow::getUserId, UserHolder.getUser().getId()).count();
        return CommonResult.success(count > 0);
    }

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 关注用户的ID
     */
    @Override
    public CommonResult<List<UserDTO>> commonFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String selfKey = "follow:" + userId;
        String aimKey = "follow:" + followUserId;

        // 获取两个用户之间的交集
        Set<String> intersectIds = stringRedisTemplate.opsForSet().intersect(selfKey, aimKey);

        // 无交集
        if (CollectionUtil.isEmpty(intersectIds)) {
            return CommonResult.success(Collections.emptyList());
        }

        // 返回交集部分的用户信息
        List<User> userList = userService.listByIds(intersectIds);
        if (CollectionUtil.isEmpty(userList)) {
            return CommonResult.success(Collections.emptyList());
        }
        List<UserDTO> userDTOList = userList.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return CommonResult.success(userDTOList);
    }
}
