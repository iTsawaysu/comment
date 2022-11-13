package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.dto.Result;
import com.sun.entity.Blog;

/**
 * @author sun
 */
public interface BlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result likeBlog(Long id);

    Result queryMyBlog(Integer current);

    Result queryHotBlog(Integer current);

    Result queryById(Long id);

    Result queryBlogLikes(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);

}
