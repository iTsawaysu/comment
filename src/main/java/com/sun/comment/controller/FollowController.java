package com.sun.comment.controller;

import com.sun.comment.common.Result;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.service.FollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private FollowService followService;

    /**
     * 关注或取关
     * @param followUserId 关注、取关的用户ID
     * @param isFollowed 是否关注
     */
    @PutMapping("/{id}/{isFollowed}")
    public Result<String> followOrNot(@PathVariable("id") Long followUserId, @PathVariable("isFollowed") Boolean isFollowed) {
        return followService.followOrNot(followUserId, isFollowed);
    }

    /**
     * 判断是否关注该用户
     * @param followUserId 关注用户的ID
     */
    @GetMapping("/or/not/{id}")
    public Result<Boolean> isFollowed(@PathVariable("id") Long followUserId) {
        return followService.isFollowed(followUserId);
    }

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 关注用户的ID
     */
    @GetMapping("/common/{id}")
    public Result<List<UserDTO>> commonFollow(@PathVariable("id") Long followUserId) {
        return followService.commonFollow(followUserId);
    }
}
