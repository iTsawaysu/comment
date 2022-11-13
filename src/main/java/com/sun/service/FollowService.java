package com.sun.service;

import com.sun.dto.Result;
import com.sun.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author sun
 */
public interface FollowService extends IService<Follow> {

    Result followOrNot(Long followUserId, Boolean isFollowed);

    Result isFollowed(Long followUserId);

    Result commonFollow(Long followUserId);
}
