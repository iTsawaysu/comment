package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.Result;
import com.sun.dto.ScrollResult;
import com.sun.dto.UserDTO;
import com.sun.entity.Blog;
import com.sun.entity.Follow;
import com.sun.entity.User;
import com.sun.mapper.BlogMapper;
import com.sun.service.BlogService;
import com.sun.service.FollowService;
import com.sun.service.UserService;
import com.sun.utils.SystemConstants;
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

import static com.sun.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.sun.utils.RedisConstants.FEED_KEY;

/**
 * @author sun
 */
@Service
@SuppressWarnings("ALL")
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSucceed = save(blog);
        if (BooleanUtil.isFalse(isSucceed)) {
            return Result.fail("发布失败～");
        }
        // 3. 查询笔记作者的所有粉丝
        List<Follow> followUserList = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
        if (followUserList.isEmpty() || followUserList == null) {
            return Result.ok(blog.getId());
        }
        // 4. 推送笔记给所有粉丝
        for (Follow follow : followUserList) {
            // 粉丝ID
            Long userId = follow.getUserId();
            // 推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5. 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 判断当前登录用户是否点过赞。
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        // `ZSCORE key member` ：获取 SortedSet 中指定元素的 score 值（如果不存在，则代表未点过赞）。
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 2. 未点过赞：点赞，数据库点赞数 +1，将用户保存到 Redis 的 Set 集合中。
        if (score == null) {
            Boolean isSucceed = update().setSql("liked = liked + 1").eq("id", id).update();
            if (BooleanUtil.isTrue(isSucceed)) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 3. 已点过赞：取消赞，数据库点赞数 -1，将用户从 Redis 的 Set 集合中移除。
            Boolean isSucceed = update().setSql("liked = liked - 1").eq("id", id).update();
            if (BooleanUtil.isTrue(isSucceed)) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 展示热门 Blog
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogWithUserInfo(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 展示 Blog 详情页（根据 ID）
     */
    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        queryBlogWithUserInfo(blog);

        // 该用户是否点赞 Blog
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * Blog 详情页展示最早点赞的 5 个用户
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;

        // 1. 查询最早五个点赞的用户
        Set<String> topFive = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (topFive == null || topFive.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 解析出其中的 用户ID
        List<Long> userIdList = topFive.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        String userIdStrWithComma = StrUtil.join(", ", userIdList);

        // 3. 根据 ID 批量查询
        List<UserDTO> userDTOList = userService.query()
                .in("id", userIdList)
                .last("ORDER BY FIELD(id, " + userIdStrWithComma + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOList);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2. 查询收件箱 ZREVRANGEBYSCORE key max min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> tupleSet = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (tupleSet.isEmpty() || tupleSet == null) {
            return Result.ok("关注一些人去吧～");
        }

        // 3. 解析数据：blogId、lastId（最小时间戳）、offset
        List<Long> blogIdList = new ArrayList<>(tupleSet.size());
        long minTime = 0;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tupleSet) {
            blogIdList.add(Long.valueOf(tuple.getValue()));
            // 时间戳（最后一个元素即为最小时间戳）
            long time = tuple.getScore().longValue();

            // 假设时间戳为：5 4 4 2 2
            // 5 != 0 --> minTime=5; nextOffset = 1;
            // 4 != 5 --> minTime=4; nextOffset = 1;
            // 4 == 4 --> minTime=4; nextOffset = 2;
            // 2 != 4 --> minTime=2; nextOffset = 1;
            // 2 == 2 --> minTime=2; nextOffset = 2;
            if (time == minTime) {
                nextOffset ++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }

        // 4. 根据 ID 查询 Blog
        String blogIdStr = StrUtil.join(", ", blogIdList);
        List<Blog> blogList = lambdaQuery().in(Blog::getId, blogIdList).last("ORDER BY FIELD(id, " + blogIdStr + ")").list();
        for (Blog blog : blogList) {
            // 完善 Blog 数据：查询并且设置与 Blog 有关的用户信息，以及 Blog 是否被该用户点赞
            queryBlogWithUserInfo(blog);
            isBlogLiked(blog);
        }

        // 5. 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(nextOffset);
        return Result.ok(scrollResult);
    }

    private void queryBlogWithUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    private void isBlogLiked(Blog blog) {
        String key = BLOG_LIKED_KEY + blog.getId();
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点过赞
            return;
        }
        Long userId = user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
