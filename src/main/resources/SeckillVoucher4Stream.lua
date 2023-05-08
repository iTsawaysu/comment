local voucherId = ARGV[1];
local userId = ARGV[2];
local orderId = ARGV[3]

local stockKey = "seckill:stock:" .. voucherId;
local orderKey = "seckill:order:" .. voucherId;

-- 判断库存是否充足
if (redis.call('get', stockKey) < 1) then
    return 1;
end;

-- 判断用户是否下过单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2;
end;

-- 执行到此处说明库存充足且用户未下过单：扣减库存并将用户 ID 存入 Set
redis.call('hincrby', stockKey, -1);
redis.call('sadd', orderKey, userId);
-- 发送消息到 stream.orders 队列中（消息唯一 ID 由 Redis 自动生成）
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId);
return 0;
