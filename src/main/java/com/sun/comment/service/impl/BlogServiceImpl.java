package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.dto.ScrollResult;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.entity.Blog;
import com.sun.comment.mapper.BlogMapper;
import com.sun.comment.service.BlogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author sun
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    /**
     * 按照点赞数降序排序，分页查询 Blog（包括笔记信息和用户信息）
     */
    @Override
    public Result<List<Blog>> getHotBlogs(Integer current) {
        return null;
    }

    /**
     * 根据 id 获取 Blog 详情（包括笔记信息和用户信息）
     */
    @Override
    public Result<Blog> getBlogDetailById(Long id) {
        return null;
    }

    /**
     * 实现点赞功能
     */
    @Override
    public Result<String> likeBlog(Long id) {
        return null;
    }

    /**
     * 获取最早点赞的 5 个用户
     */
    @Override
    public Result<List<UserDTO>> getTopFiveUserLikedBlog(Long id) {
        return null;
    }

    /**
     * 发布笔记（保存 Blog 到数据库的同时，推送消息到粉丝的收件箱）
     */
    @Override
    public Result<Long> publishBlog(Blog blog) {
        return null;
    }

    /**
     * 获取当前用户收件箱中的 Blog（关注的人发布的 Blog）
     * @param max 上次查询的最小时间戳（第一次查询为当前时间戳）
     * @param offset 偏移量（第一次查询为 0）
     * @return Blog 集合 + 本次查询的最小时间戳 + 偏移量
     */
    @Override
    public Result<ScrollResult> getBlogsOfIdols(Long max, Integer offset) {
        return null;
    }
}
