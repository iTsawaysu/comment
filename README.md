# comment
**Redis 学习 —— 黑马点评案例**


```shell
Comment
├── config ：存放项目依赖相关配置；
│   ├── RedisConfiguration：创建单例 Redisson 客户端。
│   └── WebMvcConfiguration：配置了登录、自动刷新登录 Token 的拦截器。
│
├── controller ：存放 Restful 风格的 API 接口。
│
├── interceptor ：登录拦截器 & 自动刷新 Redis 登录 Token 有效期。
│
├── mapper ：存放操作数据库的代码。
│
├── service ：存放业务逻辑处理代码。
│   ├── BlogService：基于 Redis 实现点赞、按时间排序的点赞排行榜；基于 Redis 实现拉模式的 Feed 流。
│   ├── FollowService：基于 Redis 集合实现关注、共同关注。
│   ├── ShopService：基于 Redis 缓存优化店铺查询性能；基于 Redis GEO 实现附近店铺按距离排序。
│   ├── UserService： 基于 Redis 实现短信登录（分布式 Session）。
│   ├── VoucherOrderService：基于 Redis 分布式锁、Redis + Lua 两种方式，结合消息队列，共同实现秒杀和一人一单功能。
│   └── VoucherService ：添加优惠券，并将库存保存在 Redis 中，为秒杀做准备。
│
└── utils ：存放项目内通用的工具类。
    ├── RedisIdWorker.java ：基于 Redis 的全局唯一自增 ID 生成器。
    ├── SimpleDistributedLockBasedOnRedis.java ：简单的 Redis 锁实现，了解即可，一般用 Redisson。
    └── UserHolder.java ：线程内缓存用户信息。
```
