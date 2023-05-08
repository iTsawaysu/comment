local voucherId = ARGV[1];
local userId = ARGV[2];

-- Lua 中的拼接使用的是两个点
local stockKey = "seckill:stock:" .. voucherId;
local orderKey = "seckill:order:" .. voucherId;

-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) < 1) then
    return 1;
end;

-- 判断用户是否下过单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2;
end;

-- 执行到此处说明库存充足且用户未下过单：扣减库存并将用户 ID 存入 Set
redis.call('incryby', stockKey, -1);
redis.call('sadd', orderKey, userId);
return 0;
