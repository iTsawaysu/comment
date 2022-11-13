# comment

***Redis 学习 —— 黑马点评案例***

# 代码导入
### 后端代码导入
1. 运行 SQL 脚本；
2. 修改 `application.yml` 中的 数据库信息 和 Redis 信息；
3. 向 Redis 中插入一张秒杀优惠券（注意：beginTime 和 endTime 的设置）。
```json
// 请求地址：http://localhost:8081/voucher/seckill
{
    "shopId": 7,
    "title": "100元代金券",
    "subTitle": "周一到周五均可使用",
    "rules": "全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食",
    "payValue": 8000,
    "actualValue": 10000,
    "type": 1,
    "stock": 100,
    "beginTime":"2022-10-18T8:40:00",
    "endTime":"2022-10-18T23:40:00"
}
```


### 前端代码导入

- **Windows**：在 nginx 目录下打开 CMD 窗口，输入 `start nginx.exe`；

- **Mac OS**：

  ```shell
  brew install nginx
  
  # 查看 Nginx 安装地址
  brew info nginx
    /opt/homebrew/var/www
    /opt/homebrew/etc/nginx/nginx.conf
  
  # 将前端项目中 html/hmdp 复制到 /opt/homebrew/var/www 目录下
  # 将前端项目中 conf/nginx.conf 复制到 /opt/homebrew/etc/nginx/nginx.conf 替换并修改
  ```


# 项目介绍

> **后端**

**Spring 相关：**

- Spring Boot 2.x
- Spring MVC

**数据存储层：**

- MySQL：存储数据
- MyBatis Plus：数据访问框架

**Redis 相关：**

- spring-data-redis：操作 Redis
- Lettuce：操作 Redis 的高级客户端
- Apache Commons Pool：用于实现 Redis 连接池
- Redisson：基于 Redis 的分布式数据网格

**工具库：**

- HuTool：工具库合集
- Lombok：注解式代码生成工具



> **前端**

前端不是本项目的重点，了解即可。

- 原生 HTML、CSS、JS 三件套
- Vue 2（渐进式使用）
- Element UI 组件库
- Axios 请求库



```shell
Comment
├── config ：存放项目依赖相关配置；
│   ├── LocalDateTimeSerializerConfig.java ：解决 Json timestamp 转 LocalDateTime 的报错问题；
│   ├── MybatisPlusConfiguration.java ：配置 MyBatis Plus 分页插件；
│   ├── RedisConfiguration.java ：创建单例 Redisson 客户端；
│   ├── WebExceptionAdvice.java ：全局响应拦截器；
│   └── WebMvcConfiguration.java ：配置了登录、自动刷新登录 Token 的拦截器。
│
├── controller ：存放 Restful 风格的 API 接口；
│
├── dto ：存放业务封装类，如 Result 通用响应封装（不推荐学习它的写法）；
│
├── entity ：存放和数据库对应的 Java POJO；
│
├── interceptor ：登录拦截器 & 自动刷新 Redis 登录 Token 有效期；
│
├── mapper ：存放操作数据库的代码；
│
├── service ：存放业务逻辑处理代码；
│   ├── BlogService.java ： 基于 Redis 实现点赞、按时间排序的点赞排行榜；基于 Redis 实现拉模式的 Feed 流；
│   ├── FollowService.java ：基于 Redis 集合实现关注、共同关注；
│   ├── ShopService.java ： 基于 Redis 缓存优化店铺查询性能；基于 Redis GEO 实现附近店铺按距离排序；
│   ├── UserService.java ： 基于 Redis 实现短信登录（分布式 Session）；
│   ├── VoucherOrderService.java ：基于 Redis 分布式锁、Redis + Lua 两种方式，结合消息队列，共同实现了秒杀和一人一单功能；
│   ├── VoucherService.java ：添加优惠券，并将库存保存在 Redis 中，为秒杀做准备。
│
└── utils ：存放项目内通用的工具类；
    ├── CacheClient.java ：封装了通用的缓存工具类，涉及泛型、函数式编程等知识点；
    ├── RedisConstants.java ：保存项目中用到的 Redis 键、过期时间等常量；
    ├── RedisIdWorker.java ：基于 Redis 的全局唯一自增 ID 生成器；
    ├── SimpleDistributedLockBasedOnRedis.java ：简单的 Redis 锁实现，了解即可，一般用 Redisson；
    └── UserHolder.java ：线程内缓存用户信息。
```
