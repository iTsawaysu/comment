local key = KEYS[1]
local threadIdentifier = ARGV[1]
local autoReleaseTime = ARGV[2]

-- 判断 Redis 中的锁是否属于当前线程（不属于当前线程则表示可能因超时释放了，锁已经被其他线程获取了）
if (redis.call('hexists', key, threadIdentifier) == 0) then
    return nil;
end
-- 锁属于当前线程：重入次数 -1，然后判断重入次数是否为 0，为 0 则直接删除，否则超时续约
local counter = redis.call('hincrby', key, threadIdentifier, -1)
if (counter > 0) then
    redis.call('expire', key, autoReleaseTime);
    return nil;
else
    redis.call('del', key);
    return nil;
end
