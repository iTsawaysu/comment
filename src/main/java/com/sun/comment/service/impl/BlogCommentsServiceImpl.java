package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.entity.BlogComments;
import com.sun.comment.mapper.BlogCommentsMapper;
import com.sun.comment.service.BlogCommentsService;
import org.springframework.stereotype.Service;


/**
 * @author sun
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

}
