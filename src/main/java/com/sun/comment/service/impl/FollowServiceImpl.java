package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.entity.Follow;
import com.sun.comment.mapper.FollowMapper;
import com.sun.comment.service.FollowService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author sun
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    /**
     * 关注或取关
     * @param followUserId 关注、取关的用户ID
     * @param isFollowed 是否关注
     */
    @Override
    public Result<String> followOrNot(Long followUserId, Boolean isFollowed) {
        return null;
    }

    /**
     * 判断是否关注该用户
     * @param followUserId 关注用户的ID
     */
    @Override
    public Result<Boolean> isFollowed(Long followUserId) {
        return null;
    }

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 关注用户的ID
     */
    @Override
    public Result<List<UserDTO>> commonFollow(Long followUserId) {
        return null;
    }
}
