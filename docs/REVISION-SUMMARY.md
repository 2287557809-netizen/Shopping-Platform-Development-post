# Revision Summary — response to the review of 25 November

**Student:** Pei Shenghui
**Project:** ShopStream — distributed e-commerce platform
**Course:** Distributed Systems, University of Miskolc

---

## 0. Summary

You were right on every point. The five places where the previous version used
an alternative implementation have been replaced by the approach written in the
semester project description, and the three items that were not visible in the
report are now implemented and documented. There is no remaining deliberate
deviation from the description: if something was unclear I have followed the
literal wording of the step rather than choosing my own design.

The reason for the earlier deviation was simply familiarity — I had used
PostgreSQL, Spring Cloud Stream, the Elasticsearch Java client, a JSON cart
document and a hand-written cache in other projects, and I reached for them
instead of the tools taught in the lectures. Each alternative does the same job
technically, but none of them demonstrates the annotations and configuration the
steps are assessing, so they have all been removed.

---

## 1. The five items from your email

| # | Step | Required by the description | What the project does now | Main files |
| --- | --- | --- | --- | --- |
| 1 | Step 1 | One MySQL database per service | Two MySQL 8.4 containers, `productdb` for product-service and `orderdb` for order-service, no shared schema | `docker-compose.yml`, `config-repo/product-service.yml`, `config-repo/order-service.yml`, each `pom.xml` (`com.mysql:mysql-connector-j`) |
| 2 | Step 2 (3–5) | `KafkaTemplate` to publish, `@KafkaListener` to consume, `JsonSerializer` / `JsonDeserializer` | `spring-kafka` only; producer `KafkaTemplate<String, Object>` with `JsonSerializer` (type headers off), consumers `@KafkaListener` on `ConsumerRecord<String, EventMessage>` with `JsonDeserializer` and a per-service default type | `product-service/…/EventPublisher.java`, `order-service/…/OrderEventPublisher.java`, `*/…/*Listener.java`, `config-repo/*.yml` |
| 3 | Step 3 (3, 5) | Index maintained through an `ElasticsearchRepository`, derived methods for simple cases, `ElasticsearchOperations` for the combined query | `ProductDocument` with `@Document` / `@Field`, `ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long>` with the derived `findByCategory`, combined query built as a `NativeQuery` and executed by `ElasticsearchOperations` | `search-service/…/ProductDocument.java`, `ProductSearchRepository.java`, `SearchService.java` |
| 4 | Step 4 (2–3) | Cart as `@RedisHash("carts")` entity with a `CrudRepository`, keyed by userId, expiring via `@TimeToLive` | `Cart` annotated `@RedisHash("carts")` with `@Id userId` and `@TimeToLive`; `CartRepository extends CrudRepository<Cart, String>`; key `carts:{userId}`, TTL 1800 s refreshed on every write | `cart-service/…/Cart.java`, `CartItem.java`, `CartRepository.java`, `CartService.java` |
| 5 | Step 4 (5) | `GET /products/{id}` cached with `@Cacheable`, evicted with `@CacheEvict` when the product changes or stock is reserved | `@Cacheable(cacheNames = "products", key = "#id")` on the read, `@CacheEvict` on update and delete; the inventory path evicts through `ProductCacheEvictor` after the reservation commits | `product-service/…/ProductService.java`, `ProductCacheEvictor.java`, `ReservationService.java` |

### 1.1 Database (Step 1)

PostgreSQL is gone from the project: no `postgres` image, no driver, no
`jdbc:postgresql` URL anywhere. Both services talk to MySQL 8.4 with
`com.mysql.cj.jdbc.Driver`, and the two containers have their own named volume
so the data of one service cannot be seen by the other.

### 1.2 Kafka (Step 2, tasks 3–5)

Spring Cloud Stream is gone; `spring-cloud-stream` is not a dependency of any
module. The producer is a single class per service:

```java
kafkaTemplate.send(topic, key, event)   // KafkaTemplate<String, Object>, JsonSerializer
```

The key is the aggregate id: `orderId` for `order-events` and `inventory-events`,
`productId` for `product-events`. The consumer side is `@KafkaListener` with a
`ConsumerRecord<String, EventMessage>`, so the key, partition and offset are read
from the record itself and printed in the log line:

```
Received OrderPlaced key=41 partition=2 offset=7
```

The three topics are declared in Java with `NewTopic` / `TopicBuilder`
(`KafkaTopicConfig` in product-service and order-service), three partitions,
replication factor one. Kafka runs in KRaft mode: one `apache/kafka:3.9.1`
container with `KAFKA_PROCESS_ROLES=broker,controller` and no ZooKeeper.

The workflow is unchanged in shape but now uses the required primitives:
order-service stores the order as `PENDING` and publishes `OrderPlaced` after the
transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`);
product-service consumes it, decrements the stock of all items in one
transaction with `SELECT … FOR UPDATE`, and publishes `StockReserved` or
`StockRejected`; order-service consumes that and moves the order to `CONFIRMED`
or `REJECTED`. A redelivered `OrderPlaced` is recognised by the
`stock_reservations` primary key and republishes the stored result instead of
reserving stock twice.

### 1.3 Elasticsearch (Step 3, tasks 3 and 5)

The application no longer calls the Elasticsearch Java client directly.
`ProductDocument` carries the mapping with `@Document`, `@Id`, `@Field`
(`name` and `description` as text, `category` as keyword, `price` as double,
`stock` as integer), and `ProductEventsListener` keeps the index up to date
through the repository (`save` / `deleteById`).

`GET /search` uses two paths, as the step asks:

- `category` only, without text and price, and without an explicit sort →
  derived method `findByCategory(String category, Pageable pageable)`;
- text, category and price range together (and with sorting) → a `NativeQuery`
  with a bool query (`must` multi_match on `name^2` and `description`, `filter`
  term on `category`, `filter` range on `price`) executed with
  `ElasticsearchOperations`.

### 1.4 Cart (Step 4, tasks 2–3)

The cart is an entity again:

```java
@RedisHash("carts")
public class Cart {
    @Id private String userId;
    @TimeToLive private Long timeToLive;   // 1800 s, refreshed on every write
    ...
}
```

`CartService` only calls `findById`, `save` and `delete` on the
`CrudRepository`; there is no `RedisTemplate` code left in the cart service. The
TTL is written in the README (30 minutes,
`cart.time-to-live-seconds: 1800`). Checkout deletes the hash only after
order-service accepted the order.

### 1.5 Product cache (Step 4, task 5)

The cache-aside code has been removed from the business method. The read is
`@Cacheable(cacheNames = "products", key = "#id")`, so the method body — and the
"cache miss" log line inside it — runs only when the entry is not in Redis.
`update` and `delete` are `@CacheEvict`. For the inventory path the eviction
cannot be an annotation on `ReservationService`, because the entry must be
removed only after the stock transaction commits; `ProductCacheEvictor` holds the
`@CacheEvict` method and `ReservationService` calls it from an `afterCommit`
synchronization, so a rollback leaves a valid cache entry in place.

---

## 2. Items that were not visible in the report

| Item | Where it is implemented | Where it is shown |
| --- | --- | --- |
| Two product-service instances registered in Eureka (Step 5, task 1) | `product-service-1` and `product-service-2` in `docker-compose.yml`, same image, different `EUREKA_INSTANCE_INSTANCE_ID` | Screenshot 5.1 — Eureka dashboard with `PRODUCT-SERVICE` expanded to two instances |
| Kafka in KRaft mode, three topics with `NewTopic` / `TopicBuilder` and 3 partitions (Step 2, tasks 1–2) | `docker-compose.yml` (`KAFKA_PROCESS_ROLES`, controller quorum) and `KafkaTopicConfig` in product-service and order-service | Screenshots 2.1 and 2.2 — `docker compose ps kafka` with no ZooKeeper, and `kafka-topics.sh --describe` showing `PartitionCount: 3` |
| `orderId` / `productId` as the message key (Step 2, task 3) | `EventPublisher.publish(topic, key, type, payload)` — `String.valueOf(orderId)` in order-service, `String.valueOf(productId)` in product-service | Screenshot 2.3 — producer log `Published ProductCreated key=… partition=… offset=…`; 2.4 — consumer logs reading the same key |

The two product instances also share one Kafka consumer group
(`product-service`), so the three partitions of `order-events` are split between
them; this is the same screenshot pair 2.4 / 5.4.

---

## 3. Report structure

`docs/DESIGN-REPORT.md` now has one chapter per step, and each chapter has three
subsections:

1. **How it was developed** — the classes, the annotations and the configuration,
   with the relevant code or YAML quoted;
2. **Why this approach** — what the choice buys, in one or two paragraphs;
3. **Evidence** — the command that produces the result and the screenshot that
   shows it (`Screenshot 1.1` … `Screenshot 6.1`).

Appendix A is the checklist version of the evidence: one row per screenshot, with
the exact command behind it. The curl-only style is gone; a curl command appears
only as the way to produce a screenshot, never as the explanation of a step.

---

## 4. Verification

- Every module compiles (`test-compile`, JDK 21 target) and the nine unit test
  classes pass: 39 tests, 0 failures (product 10, order 8, cart 9, search 5,
  notification 7).
- `docker compose up -d --build` starts the whole platform with one published
  port (`8080`); `docker-compose.dev.yml` is the optional overlay used to open
  Eureka 8761, Config Server 8888, Elasticsearch 9200, Redis 6379, Kafka 29092
  and the two MySQL ports for the screenshots.
- The randomised end-to-end script `scripts/smoke.ps1` walks the same path as the
  report: create product, read it twice, search it, add it to a cart, check out,
  wait for `CONFIRMED`, and check a rejected order.

---

## 5. What is still open

Nothing. The stack was started once, the whole path was executed, and every
figure in the report is a real screenshot of that run (`docs/screenshots/`,
fourteen images). The architecture figure is generated by
`scripts/render_architecture.py`, the terminal evidence by
`scripts/evidence.ps1`, the test summary by `scripts/run-tests.ps1`, so any
figure can be reproduced instead of being trusted. The report is also exported
as `docs/ShopStream-Design-Report.pdf` by `scripts/build-report.py`.
