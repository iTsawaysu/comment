package com.sun.comment.service;

import com.sun.comment.common.CommonResult;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author sun
 */
public interface FollowService extends IService<Follow> {

    /**
     * 关注或取关
     * @param followUserId 被关注、取关的用户ID
     * @param isFollowed 是否关注
     */
    CommonResult<String> followOrNot(Long followUserId, Boolean isFollowed);

    /**
     * 判断是否关注该用户
     * @param followUserId 被关注用户的ID
     */
    CommonResult<Boolean> isFollowed(Long followUserId);

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 被关注用户的ID
     */
    CommonResult<List<UserDTO>> commonFollow(Long followUserId);
}
