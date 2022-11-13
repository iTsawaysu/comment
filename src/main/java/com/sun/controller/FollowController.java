package com.sun.controller;

import com.sun.dto.Result;
import com.sun.service.FollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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
     * @param followUserId 需要关注 or 取关的 用户ID
     * @param isFollowed 是否关注
     */
    @PutMapping("/{id}/{isFollowed}")
    public Result followOrNot(@PathVariable("id") Long followUserId, @PathVariable("isFollowed") Boolean isFollowed) {
        return followService.followOrNot(followUserId, isFollowed);
    }

    /**
     * 判断是否关注该用户
     * @param followUserId 关注用户的ID
     */
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long followUserId) {
        return followService.isFollowed(followUserId);
    }

    /**
     * 获取两个用户之间的共同关注用户
     * @param followUserId 关注用户的ID
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long followUserId) {
        return followService.commonFollow(followUserId);
    }
}
