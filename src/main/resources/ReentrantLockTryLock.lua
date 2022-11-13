---
--- Created by sun.
--- DateTime: 2022/10/17 16:22
---

local key = KEYS[1];    -- 锁名称
local threadIdentifier = ARGV[1];   -- 线程唯一标识
local releaseTime = ARGV[2];    -- 锁的自动施放时间

-- 锁不存在：获取锁并且添加线程标识 + 设置有效期
if (redis.call('EXISTS', key) == 0) then
    redis.call('HSET', key, threadIdentifier, 1);
    redis.call('EXPIRE', key, releaseTime);
    return 1;
end;

-- 锁存在，线程标识是自己：重入次数加1 + 设置有效期
if (redis.call('HEXISTS', key, threadIdentifier) == 1) then
    redis.call('HINCRBY', key, threadIdentifier, 1);
    redis.call('EXPIRE', key, releaseTime);
    return 1;
end;

-- 锁存在，线程标识不是自己，获取锁失败
return 0;
