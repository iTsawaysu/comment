package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.CommonResult;
import com.sun.comment.entity.dto.ScrollResult;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.entity.Blog;

import java.util.List;

/**
 * @author sun
 */
public interface BlogService extends IService<Blog> {

    /**
     * 按照点赞数降序排序，分页查询 Blog（包括笔记信息和用户信息）
     */
    CommonResult<List<Blog>> getHotBlogs(Integer current);

    /**
     * 根据 id 获取 Blog 详情（包括笔记信息和用户信息）
     */
    CommonResult<Blog> getBlogDetailById(Long id);

    /**
     * 实现点赞功能
     */
    CommonResult<String> likeBlog(Long id);

    /**
     * 获取最早点赞的 5 个用户
     */
    CommonResult<List<UserDTO>> getTopFiveUserLikedBlog(Long id);

    /**
     * 发布笔记（保存 Blog 到数据库的同时，推送消息到粉丝的收件箱）
     */
    CommonResult<Long> publishBlog(Blog blog);

    /**
     * 获取当前用户收件箱中的 Blog（关注的人发布的 Blog）
     * @param max 上次查询的最小时间戳（第一次查询为当前时间戳）
     * @param offset 偏移量（第一次查询为 0）
     * @return Blog 集合 + 本次查询的最小时间戳 + 偏移量
     */
    CommonResult<ScrollResult> getBlogsOfIdols(Long max, Integer offset);
}
