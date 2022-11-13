---
--- Created by sun.
--- DateTime: 2022/10/17 16:39
---

local key = KEYS[1];    -- 锁名称
local threadIdentifier = ARGV[1];   -- 线程唯一标识
local releaseTime = ARGV[2];    -- 锁的自动施放时间

-- 当前锁不是自己持有：直接返回
if (redis.call('HEXISTS', key, threadIdentifier) == 0) then
    return nil;
end;

-- 当前锁是自己持有的，重入数减1
local count = redis.call('HINCRBY', key, threadIdentifier, -1);

-- 重入数大于0：不能释放锁，重置有效期后返回
if (count > 0) then
    redis.call('EXPIRE', key, releaseTime);
    return nil;
else
    -- 重入数等于0：可以释放锁，直接删除
    redis.call('DEL', key);
    return nil;
end;
