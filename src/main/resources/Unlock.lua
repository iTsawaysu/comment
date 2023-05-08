-- KEYS[1]：锁的 Key
-- ARGV[1]：Redis 中的线程标识

-- 比较 Redis 中的线程标识与当前线程的线程标识是否一致，一致则释放锁。
if ((redis.call("get", KEYS[1])) == ARGV[1]) then
    return redis.call("del", KEYS[1]);
end
return 0
