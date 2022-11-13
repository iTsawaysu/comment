package com.sun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.entity.UserInfo;
import com.sun.mapper.UserInfoMapper;
import com.sun.service.UserInfoService;
import org.springframework.stereotype.Service;

/**
 * @author sun
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}
