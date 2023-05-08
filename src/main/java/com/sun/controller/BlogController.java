package com.sun.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.common.SystemConstants;
import com.sun.dto.ScrollResult;
import com.sun.dto.UserDTO;
import com.sun.entity.Blog;
import com.sun.exception.ThrowUtils;
import com.sun.service.BlogService;
import com.sun.service.UserService;
import com.sun.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogService blogService;

    @Resource
    private UserService userService;

    /**
     * 发布笔记（保存 Blog 到数据库的同时，推送消息到粉丝的收件箱）
     */
    @PostMapping
    public CommonResult<Long> publishBlog(@RequestBody Blog blog) {
        return blogService.publishBlog(blog);
    }

    /**
     * 按照点赞数降序排序，分页查询 Blog（包括笔记信息和用户信息）
     */
    @GetMapping("/hot")
    public CommonResult<List<Blog>> getHotBlogs(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.getHotBlogs(current);
    }

    /**
     * 根据 id 获取 Blog 详情（包括笔记信息和用户信息）
     */
    @GetMapping("/{id}")
    public CommonResult<Blog> getBlogDetailById(@PathVariable("id") Long id) {
        return blogService.getBlogDetailById(id);
    }

    /**
     * 实现点赞功能
     */
    @PutMapping("/like/{id}")
    public CommonResult<String> likeBlog(@PathVariable("id") Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        return blogService.likeBlog(id);
    }

    /**
     * 获取最早点赞的 5 个用户
     */
    @GetMapping("/likes/{id}")
    public CommonResult<List<UserDTO>> getTopFiveUserLikedBlog(@PathVariable("id") Long id) {
        return blogService.getTopFiveUserLikedBlog(id);
    }

    /**
     * 查询当前用户的 Blog
     */
    @GetMapping("/of/me")
    public CommonResult<List<Blog>> queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO userDTO = UserHolder.getUser();
        ThrowUtils.throwIf(userDTO == null, ErrorCode.OPERATION_ERROR);
        Page<Blog> blogPage = blogService.lambdaQuery().eq(Blog::getUserId, userDTO.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        ThrowUtils.throwIf(blogPage == null, ErrorCode.OPERATION_ERROR);
        return CommonResult.success(blogPage.getRecords());
    }

    /**
     * 查询指定用户的 Blog
     */
    @GetMapping("/of/user")
    public CommonResult<List<Blog>> queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                                @RequestParam(value = "id") Long id) {
        Page<Blog> blogPage = blogService.lambdaQuery().eq(Blog::getUserId, id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        ThrowUtils.throwIf(blogPage == null, ErrorCode.OPERATION_ERROR);
        return CommonResult.success(blogPage.getRecords());
    }

    /**
     * 获取当前用户收件箱中的 Blog（关注的人发布的 Blog）
     * @param max 上次查询的最小时间戳（第一次查询为当前时间戳）
     * @param offset 偏移量（第一次查询为 0）
     * @return Blog 集合 + 本次查询的最小时间戳 + 偏移量
     */
    @GetMapping("/of/follow")
    public CommonResult<ScrollResult> getBlogsOfIdols(@RequestParam("lastId") Long max,
                                                      @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.getBlogsOfIdols(max, offset);
    }
}
