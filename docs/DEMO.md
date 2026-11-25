# ShopStream — 15 分钟演示脚本

> 演示前提：`docker compose up -d --build` 已执行，`docker compose ps` 显示
> 14 个容器全部 `healthy`。所有业务请求只经过 `http://localhost:8080`，不直接
> 访问任何业务服务端口，用来证明"只暴露网关"。
>
> 建议提前打开 4 个窗口：
> 1. PowerShell（执行 curl）
> 2. `docker compose logs -f api-gateway`
> 3. `docker compose logs -f product-service-1 product-service-2 order-service`
> 4. Postman（导入 `postman/ShopStream.postman_collection.json`）

---

## 0. 开场与架构说明（0:00 – 2:00）

**要讲的内容**

- 5 个业务服务 + 3 个基础设施服务：product / order / cart / search /
  notification，以及 Eureka、Config Server、Gateway。
- 每个服务独立数据存储：两个 MySQL 8.4（productdb、orderdb）、一个 Redis、
  indexed Elasticsearch。
- 同步调用只有"读"（Feign 取价、取商品快照）；写链路的跨服务协作全部走
  Kafka 事件，最终一致。
- product-service 跑两个实例，Eureka 上能看到两个 instance。
- 唯一外部入口是网关 8080。

**现场执行**

```powershell
docker compose ps
docker compose port api-gateway 8080
```

讲解点：只有 `api-gateway` 有端口映射，其他容器的 `PORTS` 列为空或仅内部
端口。

```powershell
docker compose exec api-gateway wget -qO- http://discovery-server:8761/eureka/apps
```

讲解点：注册表里能看到 `PRODUCT-SERVICE` 下面有两个 instance
（`product-service-1`、`product-service-2`），网关和 Feign 都只依赖服务名。

---

## 1. 网关与商品 CRUD（2:00 – 5:30）

### 1.1 网关连通性

```powershell
curl.exe -i http://localhost:8080/api/products
```

观察响应头：

- `X-Request-Id`：网关全局过滤器生成或透传的请求 id。
- 这是唯一的对外入口。

### 1.2 创建商品

```powershell
curl.exe -X POST http://localhost:8080/api/products `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"Laptop Pro 14\",\"description\":\"14 inch developer laptop with 32 GB RAM\",\"category\":\"electronics\",\"price\":1299.00,\"stock\":5}'
```

```powershell
curl.exe -X POST http://localhost:8080/api/products `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"Mechanical Keyboard\",\"description\":\"Hot-swappable mechanical keyboard\",\"category\":\"accessories\",\"price\":89.00,\"stock\":10}'
```

记下返回的两个 `id`（下面用 `$PID`、`$PID2` 表示）。

```powershell
$PID = 1
$PID2 = 2
```

### 1.3 Redis 缓存：第一次 MISS，第二次 HIT

```powershell
docker compose logs --tail=5 product-service-1
curl.exe http://localhost:8080/api/products/$PID
docker compose logs --tail=5 product-service-1
```

讲解点：第一次请求打印 `Cache miss for product 1 - reading from MySQL`；
再请求一次，日志不再出现该行 —— `@Cacheable` 直接命中 Redis。

Redis 里的证据：

```powershell
docker compose exec redis redis-cli -a mypass KEYS "products::*"
docker compose exec redis redis-cli -a mypass TTL "products::$PID"
```

### 1.4 更新与缓存失效

```powershell
curl.exe -X PUT http://localhost:8080/api/products/$PID `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"Laptop Pro 14\",\"description\":\"14 inch developer laptop with 32 GB RAM\",\"category\":\"electronics\",\"price\":1249.00,\"stock\":5}'

curl.exe http://localhost:8080/api/products/$PID
```

讲解点：`@CacheEvict` 在更新时删除 `products::{id}`，下一次读又回源，所以
日志里重新出现 `Cache miss`。

---

## 2. Kafka → 搜索（5:30 – 7:00）

商品写操作提交事务后发布 `ProductCreated/Updated`，search-service 异步写入
Elasticsearch，所以第一次查询可能还需要几百毫秒。

```powershell
curl.exe "http://localhost:8080/api/search?text=laptop"
```

再演示过滤、价格区间和排序：

```powershell
curl.exe "http://localhost:8080/api/search?category=electronics&minPrice=500&maxPrice=2000&sort=priceAsc"
```

```powershell
curl.exe "http://localhost:8080/api/search?page=0&size=2&sort=nameAsc"
```

讲解点：

- 简单场景（只有 category）走 `ProductSearchRepository.findByCategory`
  派生方法；组合查询走 `ElasticsearchOperations` 的 `NativeQuery`：
  `multi_match(name^2, description)` + `term(category)` + `range(price)` +
  分页排序。
- 搜索是最终一致；日志里能看到 `Indexed product ...`。

```powershell
docker compose logs --tail=10 search-service
```

索引 mapping 也可以现场展示：

```powershell
docker compose exec elasticsearch curl -s "http://localhost:9200/products/_mapping?pretty"
```

---

## 3. 购物车（7:00 – 9:00）

### 3.1 加入商品（Feign 读取商品快照 + Redis Hash）

```powershell
curl.exe -X POST http://localhost:8080/api/carts/demo-user/items `
  -H "Content-Type: application/json" `
  -d "{\"productId\":$PID,\"quantity\":2}"
```

```powershell
curl.exe http://localhost:8080/api/carts/demo-user
```

讲解点：

- 购物车是 `@RedisHash("carts")` 实体，key 为 `carts:demo-user`，由
  `CrudRepository<Cart, String>` 管理，不再是一整个 JSON 文档。
- 添加时通过 Feign 读取当前名称/单价/库存，并校验数量不超过库存。
- 每次写操作刷新 TTL（`@TimeToLive`，1800 秒 = 30 分钟）。

```powershell
docker compose exec redis redis-cli -a mypass HGETALL carts:demo-user
docker compose exec redis redis-cli -a mypass TTL carts:demo-user
```

### 3.2 修改数量与删除

```powershell
curl.exe -X PUT http://localhost:8080/api/carts/demo-user/items/$PID `
  -H "Content-Type: application/json" `
  -d '{\"quantity\":3}'
```

```powershell
curl.exe -X PUT http://localhost:8080/api/carts/demo-user/items/$PID `
  -H "Content-Type: application/json" `
  -d '{\"quantity\":0}'
```

讲解点：数量置 0 等于删除该条目；总价由服务端重新计算。

再把数量加回 2，准备结算：

```powershell
curl.exe -X POST http://localhost:8080/api/carts/demo-user/items `
  -H "Content-Type: application/json" `
  -d "{\"productId\":$PID,\"quantity\":2}"
```

---

## 4. 下单 + 库存 Saga（9:00 – 12:00）

### 4.1 从购物车结算

```powershell
curl.exe -X POST http://localhost:8080/api/carts/demo-user/checkout
```

记下返回的 `id` 作为 `$OID`。

```powershell
$OID = 1
```

讲解点：cart-service 通过 Feign 调 order-service 创建订单；下单成功后才删除
购物车 hash。可以立刻验证：

```powershell
curl.exe http://localhost:8080/api/carts/demo-user
```

### 4.2 观察状态机：PENDING → CONFIRMED

```powershell
curl.exe http://localhost:8080/api/orders/$OID
```

第一次很可能看到 `PENDING`（`OrderPlaced` 已发出，等库存结果）。

```powershell
Start-Sleep -Seconds 2
curl.exe http://localhost:8080/api/orders/$OID
```

第二次应是 `CONFIRMED`。

讲解点（这是本项目的核心难点）：

1. order-service 先用 Feign 取当前单价，保存 `PENDING` 订单和价格快照，
   提交事务后才发 `OrderPlaced`（`@TransactionalEventListener(AFTER_COMMIT)`
   + `KafkaTemplate`，key = orderId）。
2. product-service 的 `@KafkaListener`（group `product-service`）消费
   `OrderPlaced`，在**一个事务**里按 `productId` 排序后逐个
   `SELECT ... FOR UPDATE`，任一商品不足就整体回滚，并发不会超卖。
3. 成功写 `stock_reservations(RESERVED)` 并发 `StockReserved`；失败在独立
   事务里写 `REJECTED` 并发 `StockRejected`；两条消息 key 都是 orderId。
4. order-service 只允许 `PENDING -> CONFIRMED/REJECTED` 一次，重复消息是
   空操作。
5. `stock_reservations.order_id` 是主键，重复的 `OrderPlaced` 不会重复扣库存。

同时看日志（注意两个 product 实例分属不同 partition）：

```powershell
docker compose logs --tail=30 product-service-1 product-service-2
docker compose logs --tail=30 order-service
```

日志里能看到 `Received OrderPlaced key=1 partition=… offset=…`、
`Published StockReserved key=1 partition=… offset=…`。

### 4.3 库存不足 → REJECTED

```powershell
curl.exe -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -d "{\"items\":[{\"productId\":$PID,\"quantity\":999}]}"
```

```powershell
$OID2 = 2
Start-Sleep -Seconds 2
curl.exe http://localhost:8080/api/orders/$OID2
```

讲解点：`status=REJECTED`，`failureReason` 形如
`Insufficient stock for product 1: requested 999, available 3`；库存没有被部分
扣减，因为扣减在同一个事务里。

也可以用 `stock_reservations` 表直接证明幂等记录（MySQL 客户端）：

```powershell
docker compose exec product-mysql mysql -uproduct -pproduct-pw productdb `
  -e "select order_id, status, reason from stock_reservations order by order_id;"
```

---

## 5. 通知服务与异步链路可观察性（12:00 – 13:30）

notification-service 用消费组 `notification-service` 订阅 `order-events` 和
`inventory-events`，只记日志、不落库（课程要求），额外提供 REST 方便演示。

```powershell
curl.exe "http://localhost:8080/api/notifications?limit=20"
```

```powershell
curl.exe http://localhost:8080/api/notifications/$OID
```

```powershell
curl.exe http://localhost:8080/api/notifications/$OID2
```

讲解点：

- 同一个订单会依次出现 `OrderPlaced`、`StockReserved`、`OrderConfirmed`。
- 用 `eventId` 去重，Kafka 重投不会产生重复通知。
- 独立消费组不会抢走业务服务的消息。

---

## 6. 测试与工程化（13:30 – 15:00）

### 6.1 单元测试

```powershell
cd product-service; .\mvnw.cmd -q test
cd ..\order-service; .\mvnw.cmd -q test
cd ..\cart-service; .\mvnw.cmd -q test
cd ..\search-service; .\mvnw.cmd -q test
cd ..\notification-service; .\mvnw.cmd -q test
```

讲解点：JUnit 5 + Mockito，不依赖 Docker；覆盖商品 CRUD、库存预约与拒绝、
幂等重复消息、订单状态迁移、购物车 TTL/结算、ES 事件处理、通知去重。

### 6.2 配置中心与发现

```powershell
docker compose logs --tail=15 config-server
docker compose logs --tail=15 api-gateway
```

讲解点：

- 业务服务 `spring.config.import=configserver:` 且 `fail-fast=true`，配置
  中心不可用就快速失败；MySQL URL、Kafka 地址、consumer group、三个 topic
  名字都在 `config-repo/`。
- 网关和 Feign 只用 Eureka 服务名，不写死容器地址。

### 6.3 收尾

```powershell
docker compose ps
```

一句话总结：**网关唯一入口 + 每服务独立 MySQL/Redis/ES + Feign 只做读 +
Kafka 事件最终一致 + 幂等库存 Saga + 两个 product 实例**。

---

## 备用命令

清空演示数据（可选，停止后删除卷）：

```powershell
docker compose down -v
```

只重建某个服务：

```powershell
docker compose up -d --build product-service-1 product-service-2
```

查看 Kafka 主题与消费组：

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic order-events
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group product-service
```

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 搜索刚创建的商品为空 | Kafka 异步索引还没追上 | 等 1–2 秒重试，展示 search-service 日志 |
| 订单一直是 `PENDING` | product-service 未启动或消费失败 | `docker compose logs product-service-1`，确认 `StockReserved` 已发出 |
| 首次启动很慢 | 镜像构建 + MySQL/Kafka/ES 启动 | 等待 healthcheck 全部通过 |
| 端口 8080 被占用 | 本机已有服务 | 修改 Compose 中网关映射或先停止占用进程 |
| `products::*` 为空 | 商品未被读取，或已过期（2 分钟） | 先 GET 一次商品再查 Redis |
