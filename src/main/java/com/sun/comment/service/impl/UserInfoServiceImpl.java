package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.entity.UserInfo;
import com.sun.comment.mapper.UserInfoMapper;
import com.sun.comment.service.UserInfoService;
import org.springframework.stereotype.Service;

/**
 * @author sun
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}
