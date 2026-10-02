# 外卖订餐系统

前后端分离的外卖订餐系统：Spring Boot 后端 + Vue 3 商家管理端 + 微信小程序用户端。

## 功能特性

业务规则与数据一致性全部由后端实现，经 `/admin/**`（商家侧）与 `/user/**`（顾客侧）接口提供；前端只做表单校验、路由守卫与展示。

### 账号与权限

- 用户端只支持微信登录（`jscode2session`），首次登录自动建号，没有密码或注册入口
- 员工账号只能由店长创建和管理，不支持自助注册；初始密码由店长指定，编辑资料不改密码，改密码走单独接口
- 员工分店长（`role = 1`）与店员（`role = 0`，新建默认）两种角色。员工管理、数据报表与营业状态切换仅店长可用，由 `JwtTokenAdminInterceptor` 拦截并返回 403；登出、改密码与营业状态查询对所有已登录员工放行
- 管理端与用户端的令牌各用一套密钥，并携带权限版本：改角色、启停账号、改密码或退出登录后旧令牌立即失效（401，前端跳回登录页）；前端路由守卫另按角色拦截 `/employees` 与 `/reports`
- 登录连续失败 5 次锁定 15 分钟，按来源 IP 计数以免用已知账号把他人锁死；失败提示统一为「用户名或密码错误」，账号不存在时同样执行一次等价散列校验，无法用于枚举
- 密码以加盐散列（Argon2id）存储

### 菜单与商品

- 分类、菜品（含口味）与套餐的增删改查和启用/停用
- 禁用分类等于关掉整类商品：该类菜品与套餐从用户端菜单和管理端筛选列表中消失，无法加购与下单；禁用不改动商品状态，重新启用即恢复
- 单独停售某个菜品或套餐，同样无法加购与下单
- 图片上传限 jpg/jpeg/png/gif/bmp/webp、单文件 ≤5MB，由后端兜底校验（只验后缀），不依赖前端
- 菜品与套餐列表查询带缓存，商品增删改或分类启停时整体失效

### 订单与支付

- 用户端可下单、支付、分页查询历史订单、再来一单、取消订单与催单
- 商家端可条件分页查询订单与详情，办理接单/拒单/派单/完成/取消，并查看各状态订单数量
- 并发下单以唯一索引防重号，加购按「用户 + 商品 + 口味」在数据库层去重
- 支付为模拟实现：下单即视为已支付，无真实资金流。回调接口已预留，日志只记商户订单号，原文与明文都不落盘；接入真实支付时，改为在 `PUT /user/order/payment` 中调用微信下单、由回调驱动状态，取消与拒单的退款尚未接入

### 数据统计与营业状态

- 今日概览含营业额、有效订单、新增用户与订单完成率，以及订单、菜品、套餐总览，对全部员工开放
- 区间报表含营业额、用户、订单统计与销量 Top10，仅店长可访问
- 营业状态全部员工可查，仅店长可切换

### 实时推送

- 向所有在线管理端推送来单（type 1）、催单（type 2）与订单状态变更（type 3）三类消息，多终端的订单列表与概览随之同步；管理端对来单、催单弹提醒，状态变更只刷新
- 建连时校验令牌签名、账号启用状态与权限版本，未通过即断开

### 定时任务

- 每 2 分钟取消下单满 15 分钟仍未支付的订单
- 每小时将下单满 3 小时且仍在派送中的订单置为完成

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 2.7.3（MVC / Validation / Task / WebSocket / Cache） |
| 构建 | Maven 多模块（sky-common / sky-pojo / sky-server） |
| 数据访问 | MyBatis、PageHelper（分页）、Druid（连接池）、MySQL |
| 缓存 | Redis（Spring Data Redis + Spring Cache） |
| 认证与安全 | jjwt 0.9.1（JWT 双密钥）、BouncyCastle（Argon2id 密码散列）、微信支付 APIv3 客户端（回调解密） |
| 对象存储 | 阿里云 OSS SDK（图片上传） |
| 管理端前端 | Vue 3、Vite 5、TypeScript、Element Plus、Pinia、Vue Router、ECharts、Axios、Day.js |
| 用户端 | 微信小程序 |

## 目录结构

```
sky-take-out
├── pom.xml                # 父工程
├── sky-common             # 公共模块：constant、context、exception、json、properties、result、utils
├── sky-pojo               # 数据模型：entity / dto / vo
├── sky-server             # 服务端：config、controller（admin / user / notify）、handler、interceptor、
│                          #   mapper、service、task、websocket；资源见下
│   └── src/main/resources #   application.yml、mapper/*.xml；application-dev.yml 不在版本库中
├── sql                    # 数据库初始化脚本（origin.sql：建库建表 + 初始店长）
├── merchant-admin         # 商家管理端
└── from_ChuanZhiHeiMa_WeChatMiniProgram   # 用户端（微信小程序）
```

## 快速开始

### 1. 环境要求

| 环境 | 版本 |
| --- | --- |
| JDK | 17+ |
| Node.js / npm | 24.13.0 / 11.6.2 |
| MySQL | 9.7（3306） |
| Redis | Memurai 4.2.3（Windows 上的 Redis 替代，兼容 Redis 7.4，6379） |
| 微信开发者工具 | 2.02.2608060（调试基础库 2.24.4） |

### 2. 准备 application-dev.yml

`sky-server/src/main/resources/application-dev.yml` 不在版本库中，需自行新建，否则 `${sky.*}` 占位符无法解析、启动即报错。端口、JWT 有效期与令牌头名等在 `application.yml` 已有默认值，这里只补取值：

```yaml
sky:
  datasource:                  # 数据库
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: sky_take_out
    username: root
    password: 你的数据库密码
  redis:                       # Redis
    host: localhost
    port: 6379
  jwt:                         # 两端必须用不同的随机密钥
    admin-secret-key: "请替换为随机字符串"
    user-secret-key: "请替换为另一段随机字符串"
  alioss:                      # 阿里云 OSS
    endpoint: oss-cn-beijing.aliyuncs.com
    access-key-id: 你的 AccessKeyId
    access-key-secret: 你的 AccessKeySecret
    bucket-name: 你的 BucketName
  wechat:                      # 微信小程序
    appid: 你的 appid
    secret: 你的 secret
```

- **阿里云 OSS（`sky.alioss`）**：不配置时仅管理端图片上传不可用，其余功能不受影响。
- **微信（`sky.wechat`）**：不配置时用户端无法登录。用自己的小程序时，`secret` 填此处，`appid` 需与小程序侧一致——`from_ChuanZhiHeiMa_WeChatMiniProgram/project.config.json` 中的 `appid` 已脱敏为 `wx0000000000000000`，请替换为自己的 AppID。

### 3. 初始化数据库

在**项目根目录**（与 `pom.xml` 同级、包含 `sql/` 的那一层）的 cmd 中执行：

```cmd
mysql -uroot -p < sql\origin.sql
```

脚本会建好 `sky_take_out` 库、11 张表与初始店长（用户名 `admin`、密码 `123456`），除店长外不含其他业务数据。

建的表为：`address_book`、`category`、`dish`、`dish_flavor`、`employee`、`order_detail`、`orders`、`setmeal`、`setmeal_dish`、`shopping_cart`、`user`（已含并发去重所需的唯一索引）。

店长密码先以 32 位小写无盐 MD5 写入，首次登录成功后由后端自动升级为 Argon2id。登录后请尽快修改初始密码，并把脱敏的联系方式与证件号改成真实信息。菜单数据（分类、菜品与口味）需登录后在管理端录入。

### 4. 启动后端、商家管理端，运行小程序

## 当前限制

本项目是**本地运行的开发/演示版本，非生产可用**，请勿直接对外部署。以下差距按上线受阻程度从高到低排列，且不限于此：
- **支付为模拟实现**：下单后未调用微信下单，直接视为已支付，没有真实资金流，退款也未接入。真实下单的接入位置已在代码中标注
- **回调未验签**：`/notify/paySuccess` 只解密报文，既未校验微信签名头，也未核对金额，接入真实支付前必须补齐
- **多实例推送受限**：WebSocket 会话存在单机内存，多实例部署时推送无法跨实例送达，需改用 Redis 等外部存储；其余并发场景已由唯一索引、条件更新与 Redis 锁处理
- **并发上限由连接池决定**：Tomcat 线程数未调整（默认 200），Druid 连接池设为 5~20（`max-active: 20`，等待超时 60s），Redis 未配连接池、由 Lettuce 复用单个连接。数据库密集的接口同时只能处理约 20 个请求，超出即在池中排队，整体并发量级在几十以内
- **定时任务串行执行**：未自定义调度线程池，两个 `@Scheduled` 任务共用默认的单线程调度器，同一时刻只运行一个
- **部分配置未外部化**：小程序后端地址写在 `common/vendor.js` 中，AppID 写在 `project.config.json` 中（已脱敏为占位值），按环境调整需直接改文件

## 许可证

MIT
