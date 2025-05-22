package com.sun.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.SystemConstants;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.Blog;
import com.sun.comment.entity.Follow;
import com.sun.comment.entity.User;
import com.sun.comment.entity.dto.ScrollResult;
import com.sun.comment.entity.dto.UserDTO;
import com.sun.comment.mapper.BlogMapper;
import com.sun.comment.service.BlogService;
import com.sun.comment.service.FollowService;
import com.sun.comment.service.UserService;
import com.sun.comment.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.sun.comment.common.RedisConstants.BLOG_LIKED_KEY;
import static com.sun.comment.common.RedisConstants.FEED_KEY;

/**
 * @author sun
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
    @Resource
    private UserService userService;
    @Resource
    private FollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 按照点赞数降序排序，分页查询 Blog（包括笔记信息和用户信息）
     */
    @Override
    public CommonResult<List<Blog>> getHotBlogs(Integer current) {
        Page<Blog> pageInfo = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
        Page<Blog> page = this.lambdaQuery().orderByDesc(Blog::getLiked).page(pageInfo);
        List<Blog> records = page.getRecords();
        ThrowUtils.throwIf(records == null, ErrorCode.NOT_FOUND_ERROR);
        records.forEach(blog -> {
            // 设置 Blog 中用户相关的属性值
            setUserInfo(blog);
            // 判断当前登录用户是否点赞过 Blog
            checkBlogLiked(blog);
        });
        return CommonResult.success(records);
    }

    /**
     * 根据 id 获取 Blog 详情（包括笔记信息和用户信息）
     */
    @Override
    public CommonResult<Blog> getBlogDetailById(Long id) {
        Blog blog = this.getById(id);
        ThrowUtils.throwIf(blog == null, ErrorCode.NOT_FOUND_ERROR, "id 为 " + id + " 的笔记不存在");
        setUserInfo(blog);
        checkBlogLiked(blog);
        return CommonResult.success(blog);
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "id 为 " + userId + " 的用户不存在");
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void checkBlogLiked(Blog blog) {
        String blogLikedKey = BLOG_LIKED_KEY + blog.getId();
        UserDTO user = UserHolder.getUser();
        // 未登录时 user 为 null，无需查询当前用户是否点赞过
        if (user == null) {
            return;
        }
        // ZSCORE key member：获取 ZSet 中指定元素的 score 值，不存在则代表未点过赞
        Double score = stringRedisTemplate.opsForZSet().score(blogLikedKey, user.getId().toString());
        // result 是 Boolean 类型，存在自动拆箱，通过 BooleanUtil 防止空指针
        blog.setIsLike(score != null);
    }

    /**
     * 实现点赞功能
     */
    @Override
    public CommonResult<String> likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String blogLikedKey = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(blogLikedKey, userId.toString());
        // 未点过赞
        if (score == null) {
            boolean isUpdated = this.lambdaUpdate()
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
            ThrowUtils.throwIf(!isUpdated, ErrorCode.OPERATION_ERROR);
            stringRedisTemplate.opsForZSet().add(blogLikedKey, userId.toString(), System.currentTimeMillis());
            return CommonResult.success("点赞成功");
        } else {
            // 点过赞则取消点赞
            boolean isUpdated = this.lambdaUpdate()
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id)
                    .update();
            ThrowUtils.throwIf(!isUpdated, ErrorCode.OPERATION_ERROR);
            stringRedisTemplate.opsForZSet().add(blogLikedKey, userId.toString(), System.currentTimeMillis());
            return CommonResult.success("取消点赞成功");
        }
    }

    /**
     * 获取最早点赞的 5 个用户
     */
    @Override
    public CommonResult<List<UserDTO>> getTopFiveUserLikedBlog(Long id) {
        // 从 Redis 中查询点赞该 Blog 的前 5 位用户的 id
        String blogLikedKey = BLOG_LIKED_KEY + id;
        Set<String> topFive = stringRedisTemplate.opsForZSet().range(blogLikedKey, 0, 4);
        if (CollUtil.isEmpty(topFive)) {
            return CommonResult.success(Collections.emptyList());
        }
        // 根据 id 查询用户信息，避免泄露敏感信息返回 UserDTO
        List<Long> userIdList = topFive.stream().map(Long::parseLong).toList();
        String userIdStr = StrUtil.join(",", userIdList);
        List<UserDTO> userDTOList = userService.lambdaQuery()
                .in(User::getId, userIdList)
                .last("ORDER BY FIELD(id, " + userIdStr + ")")
                .list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
        return CommonResult.success(userDTOList);
    }

    /**
     * 发布笔记（保存 Blog 到数据库的同时，推送消息到粉丝的收件箱）
     */
    @Override
    public CommonResult<Long> publishBlog(Blog blog) {
        // 保存 Blog 到数据库
        ThrowUtils.throwIf(blog == null, ErrorCode.PARAMS_ERROR);
        blog.setUserId(UserHolder.getUser().getId());
        ThrowUtils.throwIf(!this.save(blog), ErrorCode.OPERATION_ERROR, "发布笔记失败");
        // 查询该 Blogger 的粉丝
        List<Follow> fans = followService.lambdaQuery().eq(Follow::getFollowUserId, UserHolder.getUser().getId()).list();
        ThrowUtils.throwIf(CollUtil.isEmpty(fans), ErrorCode.NOT_FOUND_ERROR);
        // 推送 Blog 给所有粉丝
        for (Follow fan : fans) {
            String key = FEED_KEY + fan.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return CommonResult.success(blog.getId());
    }

    /**
     * 获取当前用户收件箱中的 Blog（关注的人发布的 Blog）
     *
     * @param max    上次查询的最小时间戳（第一次查询为当前时间戳）
     * @param offset 偏移量（第一次查询为 0）
     * @return Blog 集合 + 本次查询的最小时间戳 + 偏移量
     */
    @Override
    public CommonResult<ScrollResult> getBlogsOfIdols(Long max, Integer offset) {
        // 查询当前用户的收件箱
        String key = FEED_KEY + UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        ThrowUtils.throwIf(CollectionUtil.isEmpty(tuples), ErrorCode.NOT_FOUND_ERROR);
        // 解析数据（key-feed:userId、value-blogId、score-timestamp），解析得到 blogId、timestamp、offset
        ArrayList<Long> blogIdList = new ArrayList<>();
        long minTime = 0;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            blogIdList.add(Long.parseLong(tuple.getValue()));
            // 循环到最后一次将其赋值给 timestamp 即可拿到最小时间戳
            long time = tuple.getScore().longValue();
            // 假设时间戳为： 2 2 1
            // 2 != 0 → minTime = 2; nextOffset = 1;
            // 2 == 2 → minTime = 2; nextOffset = 2;
            // 2 != 1 → minTime = 1; nextOffset = 1;
            if (time == minTime) {
                nextOffset++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }
        // 根据 blogId 获取 Blog 并设置相关信息
        String blogIdStr = StrUtil.join(",", blogIdList);
        List<Blog> blogList = this.lambdaQuery()
                .in(Blog::getId, blogIdList)
                .last("ORDER BY FIELD(id, " + blogIdStr + ")")
                .list();
        for (Blog blog : blogList) {
            setUserInfo(blog);
            checkBlogLiked(blog);
        }
        // 封装为 ScrollResult 并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(nextOffset);
        return CommonResult.success(scrollResult);
    }
}
