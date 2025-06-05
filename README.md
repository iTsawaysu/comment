# 项目深度解析文档

## 一、后端深度解析 (Spring Boot)

### 1. 后端的核心目标与业务流程概览

本项目是一个基于Spring Boot的点评系统后端服务，核心职能包括：
- 用户管理：登录、注册、个人信息管理
- 商铺管理：商铺信息展示、查询、点评
- 博客系统：用户可发布、查看、点赞博客内容
- 关注系统：用户可关注其他用户
- 优惠券系统：包括普通优惠券和秒杀优惠券功能

后端为前端提供的核心能力包括：RESTful API接口、用户认证、数据存储与检索、业务逻辑处理、文件上传等。

### 2. 后端核心项目架构与各组成部分职责细化

项目采用典型的Spring Boot分层架构，主要组成部分如下：

- **Controller层**：`src/main/java/com/sun/comment/controller/`
    - 负责接收和处理HTTP请求，调用Service层服务，并返回响应
    - 主要文件包括：`UserController.java`、`ShopController.java`、`BlogController.java`等

- **Service层**：`src/main/java/com/sun/comment/service/`
    - 接口定义：`src/main/java/com/sun/comment/service/`
    - 接口实现：`src/main/java/com/sun/comment/service/impl/`
    - 负责封装核心业务逻辑，调用Mapper层访问数据

- **Mapper层**：`src/main/java/com/sun/comment/mapper/`
    - 基于MyBatis-Plus框架，提供数据库访问接口
    - 负责数据持久化操作

- **实体类**：`src/main/java/com/sun/comment/entity/`
    - 包含数据库表对应的实体类
    - 主要文件包括：`User.java`、`Shop.java`、`Blog.java`等

- **DTO**：`src/main/java/com/sun/comment/entity/dto/`
    - 数据传输对象，用于前后端数据交换
    - 包括：`UserDTO.java`、`LoginFormDTO.java`等

- **配置类**：`src/main/java/com/sun/comment/config/`
    - 包含各种系统配置，如Redis、MVC、Web安全等

- **工具类**：`src/main/java/com/sun/comment/utils/`
    - 提供各种通用工具方法

- **拦截器**：`src/main/java/com/sun/comment/interceptor/`
    - 处理请求拦截，如登录校验等

- **公共组件**：`src/main/java/com/sun/comment/common/`
    - 包含通用返回对象、常量定义、错误码等

### 3. 后端关键入口点和启动流程

- **启动命令**：
  ```bash
  mvn spring-boot:run
  ```
  或生产环境
  ```bash
  java -jar target/comment-xxx.jar
  ```

- **主入口文件**：`src/main/java/com/sun/comment/CommentApplication.java`
    - 包含`@SpringBootApplication`注解的主类
    - 启用了AOP代理支持：`@EnableAspectJAutoProxy(exposeProxy = true)`

### 4. 后端核心配置管理

- **主配置文件**：`src/main/resources/application.yml`
    - 包含数据库连接配置（MySQL）
    - Redis配置（地址、密码、连接池参数）
    - 服务器端口配置（8081）
    - MyBatis-Plus配置
    - 日志级别配置

- **常量配置**：
    - `src/main/java/com/sun/comment/common/RedisConstants.java`：Redis相关常量
    - `src/main/java/com/sun/comment/common/SystemConstants.java`：系统常量

- **配置读取方式**：
    - 通过`@Value`注解
    - 通过`@ConfigurationProperties`绑定配置属性到Bean
    - 通过配置类读取相关配置

### 5. 后端内部及与前端的交互路径

- **数据库交互**：
    - 通过MyBatis-Plus框架的Mapper接口（`src/main/java/com/sun/comment/mapper/`）
    - SQL映射文件位于：`src/main/resources/mappers/`

- **API处理流程**：
    - 前端请求 → Controller接收 → 调用Service → Service调用Mapper → 数据库操作 → 返回结果
    - 使用`CommonResult`（`src/main/java/com/sun/comment/common/CommonResult.java`）封装统一响应格式

- **认证与授权**：
    - 用户登录API：`src/main/java/com/sun/comment/controller/UserController.java`
    - 登录拦截器：`src/main/java/com/sun/comment/interceptor/`目录下
    - 使用Redis存储用户登录状态

### 6. 后端涉及的核心数据结构

- **实体类**（`src/main/java/com/sun/comment/entity/`）：
    - `User.java`：用户基本信息
    - `UserInfo.java`：用户详细信息
    - `Shop.java`：商铺信息
    - `ShopType.java`：商铺类型
    - `Blog.java`：博客信息
    - `BlogComments.java`：博客评论
    - `Follow.java`：用户关注关系
    - `Voucher.java`：优惠券信息
    - `SeckillVoucher.java`：秒杀优惠券
    - `VoucherOrder.java`：优惠券订单

- **DTO**（`src/main/java/com/sun/comment/entity/dto/`）：
    - `UserDTO.java`：用户信息传输对象（脱敏）
    - `LoginFormDTO.java`：登录表单数据
    - `ScrollResult.java`：滚动分页结果
    - `RedisData.java`：Redis数据封装

### 7. 后端全部功能实现路径与精准修改点

#### 用户管理功能

- **登录/注册**：
    - API路由：POST `/user/code`、POST `/user/login`
    - Controller：`src/main/java/com/sun/comment/controller/UserController.java`
    - Service：`src/main/java/com/sun/comment/service/UserService.java`
    - 实现类：`src/main/java/com/sun/comment/service/impl/UserServiceImpl.java`
    - 修改点：登录逻辑修改需要编辑`UserServiceImpl.java`中的登录方法

- **个人信息管理**：
    - API路由：GET `/user/me`、PUT `/user/info`
    - Controller：`src/main/java/com/sun/comment/controller/UserController.java`
    - Service：`src/main/java/com/sun/comment/service/UserInfoService.java`
    - 修改点：用户信息更新逻辑修改需要编辑`UserController.java`和相应Service实现类

#### 商铺功能

- **商铺查询**：
    - API路由：GET `/shop/{id}`、GET `/shop/of/type`
    - Controller：`src/main/java/com/sun/comment/controller/ShopController.java`
    - Service：`src/main/java/com/sun/comment/service/ShopService.java`
    - 修改点：商铺查询逻辑修改需要编辑`ShopServiceImpl.java`

- **商铺类型**：
    - API路由：GET `/shop-type/list`
    - Controller：`src/main/java/com/sun/comment/controller/ShopTypeController.java`
    - Service：`src/main/java/com/sun/comment/service/ShopTypeService.java`
    - 修改点：商铺类型相关逻辑需要编辑`ShopTypeServiceImpl.java`

#### 博客功能

- **博客发布/查询**：
    - API路由：POST `/blog`、GET `/blog/{id}`、GET `/blog/of/follow`
    - Controller：`src/main/java/com/sun/comment/controller/BlogController.java`
    - Service：`src/main/java/com/sun/comment/service/BlogService.java`
    - 修改点：博客发布、查询逻辑修改需要编辑`BlogServiceImpl.java`

- **博客点赞**：
    - API路由：PUT `/blog/like/{id}`
    - Controller：`src/main/java/com/sun/comment/controller/BlogController.java`
    - 修改点：点赞逻辑修改需要编辑`BlogServiceImpl.java`中的点赞方法

#### 关注功能

- **关注/取消关注**：
    - API路由：PUT `/follow/{id}/{isFollow}`、GET `/follow/common/{id}`
    - Controller：`src/main/java/com/sun/comment/controller/FollowController.java`
    - Service：`src/main/java/com/sun/comment/service/FollowService.java`
    - 修改点：关注逻辑修改需要编辑`FollowServiceImpl.java`

#### 优惠券功能

- **优惠券查询**：
    - API路由：GET `/voucher/list/{shopId}`
    - Controller：`src/main/java/com/sun/comment/controller/VoucherController.java`
    - Service：`src/main/java/com/sun/comment/service/VoucherService.java`
    - 修改点：优惠券查询逻辑修改需要编辑`VoucherServiceImpl.java`

- **优惠券秒杀**：
    - API路由：POST `/voucher-order/seckill/{id}`
    - Controller：`src/main/java/com/sun/comment/controller/VoucherOrderController.java`
    - Service：`src/main/java/com/sun/comment/service/VoucherOrderService.java`
    - 修改点：秒杀逻辑修改需要编辑`VoucherOrderServiceImpl.java`
  ```shell
  # 秒杀之前需要新增一个秒杀券，通过 localhost:8081/voucher/seckill 实现，请求体如下所示
  {
    "shopId": 1,
    "title": "100元代金券",
    "subTitle": "周一到周五均可使用",
    "rules": "全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食",
    "payValue": 8000,
    "actualValue": 10000,
    "type": 1,
    "stock": 100,
    "beginTime":"2023-04-20T13:00:00",
    "endTime":"2023-04-21T13:00:00"
  }
  ```

### 8. 后端使用的核心技术栈、框架、关键库及其应用方式

- **Spring Boot**：核心框架，提供自动配置、依赖注入等功能
- **Spring MVC**：处理Web请求
- **MyBatis-Plus**：增强版MyBatis，简化数据库操作
- **MySQL**：关系型数据库
- **Redis**：缓存、分布式锁、计数器等功能
- **Lombok**：简化Java代码
- **Jackson**：处理JSON序列化/反序列化
- **AOP**：面向切面编程，用于日志、事务等横切关注点

### 9. 后端代码组织思路与关键设计模式

- **分层架构**：Controller-Service-Mapper经典三层架构
- **DTO模式**：使用DTO对象进行数据传输，实现前后端数据解耦
- **单例模式**：Spring容器中的Bean默认为单例
- **代理模式**：Spring AOP实现
- **工厂模式**：Spring IoC容器
- **策略模式**：不同业务逻辑的实现

### 10. 后端简易的运行/开发环境设置和首次运行指南

1. **环境要求**：
    - JDK 8+
    - Maven 3.6+
    - MySQL 5.7+
    - Redis 6.0+

2. **数据库设置**：
    - 创建名为`comment`的数据库
    - 导入`src/main/resources/db`目录下的SQL脚本

3. **修改配置**：
    - 编辑`src/main/resources/application.yml`中的数据库和Redis连接信息

4. **构建运行**：
   ```bash
   mvn clean package
   java -jar target/comment-0.0.1-SNAPSHOT.jar
   ```
   或者开发环境直接运行
   ```bash
   mvn spring-boot:run
   ```

## 二、前端深度解析 (HTML/JS/CSS)

### 1. 前端的核心目标与用户交互流程概览

该项目前端是一个HTML/JS/CSS实现的点评系统前端界面，主要包括：
- 用户登录注册页面
- 首页与商铺列表
- 商铺详情页
- 博客发布与展示页面
- 个人信息页面

核心用户交互流程包括用户登录/注册、浏览商铺、查看博客、发布博客、点赞、关注等操作。

### 2. 前端核心项目架构与各组成部分职责细化

前端采用传统的HTML+JS+CSS结构，文件组织如下：

- **HTML页面**：`src/main/resources/front/hmdp/`
    - `login.html`/`login2.html`：登录页面
    - `index.html`：首页
    - `shop-list.html`：商铺列表
    - `shop-detail.html`：商铺详情
    - `blog-detail.html`：博客详情
    - `blog-edit.html`：博客编辑
    - `info.html`/`info-edit.html`：用户信息查看与编辑

- **JavaScript**：`src/main/resources/front/hmdp/js/`
    - 包含前端交互逻辑和API调用

- **CSS**：`src/main/resources/front/hmdp/css/`
    - 页面样式文件

- **图片资源**：`src/main/resources/front/hmdp/imgs/`
    - 网站使用的图片资源

### 3. 前端关键入口点和构建/启动流程

前端是传统的静态网页形式，由后端直接提供静态资源服务。

- **主入口文件**：`src/main/resources/front/hmdp/index.html`
- **部署方式**：通过Nginx或Spring Boot直接提供静态资源访问
- **开发调试**：直接通过浏览器访问HTML文件或通过后端服务访问

### 4. 前端核心配置管理

由于是传统前端，没有复杂的构建配置。主要配置包括：

- 后端API地址：在JavaScript文件中直接定义或通过变量配置
- 静态资源路径：在HTML中通过相对路径引用

### 5. 前端内部及与后端的交互路径

- **与后端API交互**：
    - 使用AJAX或Fetch API发起HTTP请求
    - JavaScript文件中封装API调用函数
    - 处理响应数据并更新DOM

- **认证状态管理**：
    - 使用Cookie或localStorage存储登录凭证
    - 请求时自动携带认证信息

### 6. 前端涉及的核心数据结构与状态管理

- **页面状态**：通过DOM操作和JavaScript变量管理
- **表单数据**：通过表单元素的value属性获取
- **API响应数据**：以JSON格式接收并处理

### 7. 前端全部功能实现路径与精准修改点

#### 登录/注册功能

- **页面文件**：`src/main/resources/front/hmdp/login.html`、`src/main/resources/front/hmdp/login2.html`
- **相关JS**：`src/main/resources/front/hmdp/js/`中的相关JS文件
- **修改点**：修改登录表单验证或样式，需要编辑login.html和相关JS文件

#### 商铺浏览功能

- **页面文件**：
    - 列表：`src/main/resources/front/hmdp/shop-list.html`
    - 详情：`src/main/resources/front/hmdp/shop-detail.html`
- **修改点**：修改商铺展示逻辑，需要编辑相应HTML和JS文件

#### 博客功能

- **页面文件**：
    - 详情：`src/main/resources/front/hmdp/blog-detail.html`
    - 编辑：`src/main/resources/front/hmdp/blog-edit.html`
- **修改点**：修改博客展示或编辑逻辑，需要编辑相应HTML和JS文件

#### 个人信息功能

- **页面文件**：
    - 查看：`src/main/resources/front/hmdp/info.html`
    - 编辑：`src/main/resources/front/hmdp/info-edit.html`
- **修改点**：修改个人信息展示或编辑逻辑，需要编辑相应HTML和JS文件

### 8. 前端使用的核心技术栈、框架、关键库及其应用方式

- **HTML5**：页面结构
- **CSS3**：样式定义
- **JavaScript**：交互逻辑
- **jQuery**：DOM操作和AJAX请求
- **常见UI库**：如Bootstrap等（根据实际使用情况）

### 9. 前端代码组织思路与关键设计模式

- **页面组织**：按功能模块划分不同HTML页面
- **JS组织**：通过不同JS文件封装不同功能模块
- **CSS组织**：通过不同CSS文件管理不同样式组件

### 10. 前端简易的运行/开发环境设置和首次运行指南

1. **环境要求**：
    - 现代浏览器（Chrome、Firefox、Edge等）

2. **开发调试**：
    - 直接在浏览器中打开HTML文件
    - 或通过后端服务访问（推荐，可解决跨域问题）

3. **Nginx配置**（可选）：
   ```
   server {
       listen 80;
       server_name localhost;
       
       location / {
           root /path/to/src/main/resources/front/hmdp;
           index index.html;
       }
       
       location /api/ {
           proxy_pass http://localhost:8081/;
       }
   }
   ```

## 三、前后端交互总结

### 1. API契约

核心API端点包括：

- **用户相关**：
    - `POST /user/code`：发送验证码
    - `POST /user/login`：用户登录
    - `GET /user/me`：获取当前用户信息

- **商铺相关**：
    - `GET /shop/{id}`：获取商铺详情
    - `GET /shop/of/type`：按类型获取商铺列表

- **博客相关**：
    - `POST /blog`：发布博客
    - `GET /blog/{id}`：获取博客详情
    - `PUT /blog/like/{id}`：点赞博客

- **关注相关**：
    - `PUT /follow/{id}/{isFollow}`：关注/取消关注用户

- **优惠券相关**：
    - `GET /voucher/list/{shopId}`：获取商铺优惠券
    - `POST /voucher-order/seckill/{id}`：秒杀优惠券

### 2. 认证流程

#### 2.1 详细认证实现机制

项目采用了基于Token的认证机制，整个认证流程涉及多个组件协同工作：

1. **验证码生成与验证**：
    - 用户请求验证码：通过`UserController.sendCode()`方法处理
    - 后端生成6位随机数字验证码：`RandomUtil.randomNumbers(6)`
    - 验证码存储在Redis中：键格式为`login:captcha:{phone}`，过期时间为2分钟
    - 日志输出验证码（实际项目中应替换为短信发送）

2. **登录处理流程**：
    - 用户提交手机号和验证码：通过`UserController.login()`方法处理
    - 参数校验：检查手机号格式和验证码是否为空
    - 验证码比对：从Redis获取存储的验证码并与提交的验证码比对
    - 用户查询与创建：检查手机号是否已注册，未注册则自动创建新用户
    - Token生成：使用UUID生成随机Token字符串
    - 用户信息脱敏：将敏感字段排除后创建`UserDTO`对象
    - 存储登录状态：将用户信息以Hash结构存入Redis，键格式为`login:token:{token}`，过期时间为30分钟
    - 返回Token：将Token返回给前端

3. **Token处理与用户信息维护**：
    - Token存储在前端：通过SessionStorage保存
    - 请求拦截器添加Token：每次请求自动添加Authorization请求头
    - 后端Token刷新机制：每次有效请求都会刷新Redis中Token的过期时间

4. **权限拦截器链**：
    - `RefreshTokenInterceptor`：优先级高(order=0)，处理所有请求，负责Token验证和用户信息获取
        - 从请求头中获取Token
        - 根据Token从Redis获取用户信息
        - 将用户信息存入ThreadLocal
        - 刷新Token有效期
    - `LoginInterceptor`：次优先级(order=1)，处理需要登录的请求路径，拦截未登录用户
        - 检查ThreadLocal中是否有用户信息
        - 无用户信息则返回401未授权状态码

5. **ThreadLocal用户信息管理**：
    - `UserHolder`工具类管理ThreadLocal中的用户信息
    - 存储用户：`saveUser(UserDTO)`
    - 获取用户：`getUser()`
    - 移除用户：`removeUser()`，在请求完成后执行，防止内存泄漏

#### 2.2 前后端认证协作流程

1. **前端登录流程**：
    - 用户在登录页面(`login.html`)输入手机号
    - 点击"发送验证码"按钮，触发`sendCode()`方法发起`POST /user/code`请求
    - 后端生成验证码并返回
    - 用户输入验证码并点击"登录"按钮，触发`login()`方法发起`POST /user/login`请求
    - 后端验证成功后返回Token
    - 前端将Token保存到SessionStorage：`sessionStorage.setItem("token", data)`
    - 页面跳转到首页或个人信息页

2. **请求携带Token机制**：
    - 前端配置Axios请求拦截器：`axios.interceptors.request.use`
    - 每次请求自动从SessionStorage获取Token并添加到请求头：`config.headers['authorization'] = token`

3. **响应处理机制**：
    - 响应拦截器：`axios.interceptors.response.use`
    - 判断请求结果：`response.data.success`
    - 处理401未授权错误：重定向到登录页面
    - 处理其他错误：显示错误信息

4. **前后端交互中的Token生命周期**：
    - 生成：用户登录成功后生成
    - 存储：前端SessionStorage，后端Redis
    - 传递：通过HTTP请求头Authorization字段
    - 验证：后端拦截器验证有效性
    - 刷新：每次有效请求自动延长有效期
    - 失效：Redis中的Token过期（30分钟无操作）或用户主动登出

### 3. 数据流

#### 3.1 "用户登录"完整数据流

1. **前端请求验证码**：
    - 用户在`login.html`页面输入手机号
    - 点击"发送验证码"按钮，触发`sendCode()`方法
    - 发起`POST /user/code?phone={phone}`请求

2. **后端生成验证码**：
    - `UserController.sendCode()`接收请求
    - 校验手机号格式
    - 生成6位随机数验证码
    - 将验证码存入Redis：`stringRedisTemplate.opsForValue().set(LOGIN_CAPTCHA_KEY + phone, captcha, TTL_TWO, TimeUnit.MINUTES)`
    - 返回成功响应（实际中应通过短信发送）

3. **前端发起登录请求**：
    - 用户输入验证码
    - 点击"登录"按钮，触发`login()`方法
    - 发起`POST /user/login`请求，提交手机号和验证码

4. **后端处理登录**：
    - `UserController.login()`接收请求
    - 调用`UserService.login()`方法
    - 校验参数有效性
    - 从Redis获取验证码并比对
    - 查询用户是否存在，不存在则创建新用户
    - 将用户对象转换为`UserDTO`对象（脱敏）
    - 生成随机Token
    - 将用户信息存入Redis：`stringRedisTemplate.opsForHash().putAll(loginUserKey, map)`
    - 设置Token过期时间：`stringRedisTemplate.expire(loginUserKey, TTL_THIRTY, TimeUnit.MINUTES)`
    - 返回Token给前端

5. **前端存储Token与后续访问**：
    - 接收Token并存储：`sessionStorage.setItem("token", data)`
    - 页面跳转到首页或个人信息页
    - 后续请求通过Axios拦截器自动添加Token到请求头

#### 3.2 "用户浏览商铺详情"完整数据流

1. **用户点击商铺卡片**：
    - 触发页面跳转到商铺详情页：`shop-detail.html?id={shopId}`

2. **前端初始化并请求数据**：
    - 页面加载时解析URL参数获取商铺ID
    - 发起`GET /shop/{id}`请求，请求拦截器自动添加Token

3. **后端拦截器处理**：
    - `RefreshTokenInterceptor`拦截请求
    - 从请求头获取Token
    - 根据Token从Redis获取用户信息
    - 将用户信息存入ThreadLocal
    - 刷新Token有效期
    - 放行请求

4. **后端Controller处理**：
    - `ShopController`接收请求
    - 调用`ShopService.queryById()`方法
    - Service层先尝试从Redis缓存获取商铺信息
    - 缓存未命中则从数据库查询并更新缓存
    - 封装结果到`CommonResult`对象并返回

5. **前端渲染数据**：
    - 接收响应数据
    - 解析JSON数据并渲染到页面DOM
    - 更新商铺详情、评分、图片等信息

这个流程展示了完整的前后端交互，包括Token验证、缓存访问、数据库查询和页面渲染等环节。

以上就是该项目的完整解析，涵盖了后端Spring Boot和前端HTML/JS/CSS两部分的详细分析，以及深入的认证流程和数据流分析。通过这份文档，您可以快速了解项目结构，找到需要修改的具体代码位置，实现高效的开发和维护工作。 
