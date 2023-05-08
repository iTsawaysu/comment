package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.common.SystemConstants;
import com.sun.dto.ScrollResult;
import com.sun.dto.UserDTO;
import com.sun.entity.Blog;
import com.sun.entity.Follow;
import com.sun.entity.User;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.BlogMapper;
import com.sun.service.BlogService;
import com.sun.service.FollowService;
import com.sun.service.UserService;
import com.sun.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sun.common.RedisConstants.BLOG_LIKED_KEY;
import static com.sun.common.RedisConstants.FEED_KEY;

/**
 * @author sun
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FollowService followService;

    /**
     * 按照点赞数降序排序，分页查询 Blog（包括笔记信息和用户信息）
     */
    @Override
    public CommonResult<List<Blog>> getHotBlogs(Integer current) {
        // 分页查询 Blog
        Page<Blog> pageInfo = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
        Page<Blog> blogPage = this.lambdaQuery()
                .orderByDesc(Blog::getLiked)
                .page(pageInfo);
        List<Blog> records = blogPage.getRecords();
        ThrowUtils.throwIf(records == null, ErrorCode.NOT_FOUND_ERROR);

        records.forEach(blog -> {
            // 设置 Blog 中用户相关的属性值
            this.setUserInfo4Blog(blog);
            // 判断当前登录用户是否点赞过 Blog
            this.isBlogLiked(blog);
        });
        return CommonResult.success(records);
    }

    /**
     * 根据 id 获取 Blog 详情（包括笔记信息和用户信息）
     */
    @Override
    public CommonResult<Blog> getBlogDetailById(Long id) {
        // 根据 id 查询 Blog
        Blog blog = this.getById(id);
        ThrowUtils.throwIf(blog == null, ErrorCode.NOT_FOUND_ERROR);

        // 设置 Blog 中用户相关的属性值
        this.setUserInfo4Blog(blog);
        // 判断当前登录用户是否点赞过 Blog
        this.isBlogLiked(blog);
        return CommonResult.success(blog);
    }

    /**
     * 设置 Blog 中用户相关的属性值
     */
    private void setUserInfo4Blog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 判断当前登录用户是否点赞过 Blog
     */
    public void isBlogLiked(Blog blog) {
        String blogLikedKey = BLOG_LIKED_KEY + blog.getId();
        UserDTO user = UserHolder.getUser();
        // 未登录时 user 为 null，无需查询当前用户是否点赞过
        if (user == null) {
            return;
        }
        Double score = stringRedisTemplate.opsForZSet().score(blogLikedKey, user.getId().toString());
        // ZSCORE key member：获取 ZSet 中指定元素的 score 值，不存在则代表未点过赞。
        blog.setIsLike(score != null);
    }

    /**
     * 实现点赞功能
     */
    @Override
    public CommonResult<String> likeBlog(Long id) {
        // 1. 判断当前用户是否点过赞
        String blogLikedKey = BLOG_LIKED_KEY + id;
        Long userId = UserHolder.getUser().getId();
        // ZSCORE key member：获取 ZSet 中指定元素的 score 值，不存在则代表未点过赞。
        Double score = stringRedisTemplate.opsForZSet().score(blogLikedKey, userId.toString());

        // 2. 未点过赞
        boolean result = false;
        if (score == null) {
            result = this.lambdaUpdate()
                    .eq(Blog::getId, id)
                    .setSql("liked = liked + 1")
                    .update();
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            stringRedisTemplate.opsForZSet().add(blogLikedKey, userId.toString(), System.currentTimeMillis());
            return CommonResult.success("点赞成功");
        } else {
            // 3. 点过赞则取消点赞
            result = this.lambdaUpdate()
                    .eq(Blog::getId, id)
                    .setSql("liked = liked - 1")
                    .update();
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            stringRedisTemplate.opsForZSet().remove(blogLikedKey, userId.toString());
            return CommonResult.success("取消点赞成功");
        }
    }

    /**
     * 获取最早点赞的 5 个用户
     */
    @Override
    public CommonResult<List<UserDTO>> getTopFiveUserLikedBlog(Long id) {
        String blogLikedKey = BLOG_LIKED_KEY + id;

        // 1. 从 Redis 中查询点赞该 Blog 的前 5 位用户的 id
        Set<String> topFive = stringRedisTemplate.opsForZSet().range(blogLikedKey, 0, 4);
        if (CollectionUtil.isEmpty(topFive)) {
            return CommonResult.success(Collections.emptyList());
        }

        // 2. 根据 id 查询用户信息，避免泄露敏感信息返回 UserDTO。
        List<Long> userIdList = topFive.stream().map(userIdStr -> Long.parseLong(userIdStr)).collect(Collectors.toList());
        String userIdStr = StrUtil.join(",", userIdList);
        List<UserDTO> userDTOList = userService.lambdaQuery()
                .in(User::getId, userIdList)
                .last("ORDER BY FIELD(id, " + userIdStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return CommonResult.success(userDTOList);
    }

    /**
     * 发布笔记（保存 Blog 到数据库的同时，推送消息到粉丝的收件箱）
     */
    @Override
    public CommonResult<Long> publishBlog(Blog blog) {
        // 1. 保存 Blog 到数据库
        ThrowUtils.throwIf(blog == null, ErrorCode.PARAMS_ERROR);
        UserDTO user = UserHolder.getUser();
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        blog.setUserId(user.getId());
        boolean result = this.save(blog);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 2. 查询该 Blogger 的粉丝
        List<Follow> fansList = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(fansList), ErrorCode.NOT_FOUND_ERROR);

        // 3. 推送 Blog 给所有粉丝
        for (Follow follow : fansList) {
            // Key 用于标识不同粉丝，每个粉丝都有一个收件箱；Value 存储 BlogId；Score 存储时间戳。
            String key = FEED_KEY + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return CommonResult.success(blog.getId());
    }

    /**
     * 获取当前用户收件箱中的 Blog（关注的人发布的 Blog）
     * @param max 上次查询的最小时间戳（第一次查询为当前时间戳）
     * @param offset 偏移量（第一次查询为 0）
     * @return Blog 集合 + 本次查询的最小时间戳 + 偏移量
     */
    @Override
    public CommonResult<ScrollResult> getBlogsOfIdols(Long max, Integer offset) {
        // 1. 查询当前用户的收件箱
        // ZREVRANGEBYSCORE key max min LIMIT offset count
        String key = FEED_KEY + UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> tupleSet = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        ThrowUtils.throwIf(CollectionUtil.isEmpty(tupleSet), ErrorCode.NOT_FOUND_ERROR);

        // 2. 解析数据（Key - feed:userId、Value - BlogId、Score - timestamp），解析得到 blogId、timestamp、offset。
        ArrayList<Long> blogIdList = new ArrayList<>();
        long minTime = 0;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tupleSet) {
            blogIdList.add(Long.parseLong(tuple.getValue()));
            // 循环到最后一次将其赋值给 timestamp 即可拿到最小时间戳。
            long time = tuple.getScore().longValue();

            // 假设时间戳为：2 2 1
            // 2 != 0 --> minTime=5; nextOffset = 1;
            // 2 == 2 --> minTime=4; nextOffset = 2;
            // 2 != 1 --> minTime=4; nextOffset = 1;
            if (time == minTime) {
                nextOffset ++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }
        // 3. 根据 BlogId 获取 Blog 并设置相关信息
        String blogIdStr = StrUtil.join(",", blogIdList);
        List<Blog> blogList = this.lambdaQuery()
                .in(Blog::getId, blogIdStr)
                .last("ORDER BY FIELD(id, " + blogIdStr + ")")
                .list();
        for (Blog blog : blogList) {
            // 设置 Blog 中用户相关的属性值
            this.setUserInfo4Blog(blog);
            // 判断当前登录用户是否点赞过 Blog
            this.isBlogLiked(blog);
        }

        // 4.封装为 ScrollResult 并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(nextOffset);
        return CommonResult.success(scrollResult);
    }
}
