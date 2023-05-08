-- 使用 Hash 存储锁：key-锁的名称、field-线程标识、value-重入次数
-- 锁的名称
local key = KEYS[1];
-- 线程标识
local threadIdentifier = ARGV[1];
-- 锁的自动释放时间
local autoReleaseTime = ARGV[2];

-- 如果 Redis 中没有锁，则获取锁并设置 TTL 时间
if (redis.call('exists', key) == 0) then
    redis.call('hset', key, threadIdentifier, '1');
    redis.call('expire', key, autoReleaseTime);
    return 1;
end

-- 如果 Redis 中有锁，并且该锁属于当前线程，则重入次数 + 1
if (redis.call('hexists', key, threadIdentifier) == 1) then
    redis.call('hincrby', key, threadIdentifier, '1');
    redis.call('expire', key, autoReleaseTime);
    return 1;
end

-- 代码执行到此处说明：Redis 中的锁不属于当前线程
return 0;
