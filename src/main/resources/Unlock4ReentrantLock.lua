-- 使用 Hash 存储锁：key-锁的名称、field-线程标识、value-重入次数
-- 锁的名称
local key = KEYS[1];
-- 线程标识
local threadIdentifier = ARGV[1];
-- 锁的自动释放时间
local autoReleaseTime = ARGV[2];

-- 判断 Redis 中的锁是否属于当前线程（不属于当前线程则代表可能超时释放了，该锁已经被其他线程获取）
if (redis.call('hexists', key, threadIdentifier) == 0) then
    return nil;
end

-- 锁属于当前线程，重入次数 -1 后：判断重入次数是否为 0；为 0 则直接删除，否则超时续约。
local count = redis.call('hincrby', key, threadIdentifier, -1);
if (count > 0) then
    redis.call('expire', key, autoReleaseTime4Lock);
    return nil;
else
    redis.call('del', key);
    return nil;
end
