package com.sun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.entity.BlogComments;
import com.sun.mapper.BlogCommentsMapper;
import com.sun.service.BlogCommentsService;
import org.springframework.stereotype.Service;


/**
 * @author sun
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

}
