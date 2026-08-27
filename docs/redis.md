# Redis Audit Event Repository

`RedisAuditEventRepository` persists audit events to Redis using
[Spring Data Redis](https://spring.io/projects/spring-data-redis). It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`), so it plugs straight into Spring Boot's actuator auditing while
adding filtering and a richer query API.

## Storage model

Events are stored in a Redis **sorted set** (`ZSET`):

- the **member** is the complete event serialized as JSON (via the library's `AuditEventMapper`);
- the **score** is the event timestamp in epoch milliseconds.

Scoring by timestamp gives time-ordered storage for free, and lets `find(principal, after, type)` push the `after`
criterion down to Redis as an efficient `ZREVRANGEBYSCORE` query; `principal` and `type` are then matched in memory.
Because the whole event is stored as JSON and reconstructed on read, no information is lost (nested `data`, application
name, correlation id, etc. all survive). All queries return events **most recent first**.

> Redis does have a dedicated time-series type, but only via the RedisTimeSeries module (Redis Stack), which Spring
> Data Redis does not wrap. The sorted-set approach above gives equivalent time-range querying using core Redis.

## Dependencies

`RedisAuditEventRepository` uses `spring-data-redis` — an **optional** dependency of this library (you opt in by
declaring it) — plus a Redis client (Lettuce is the Spring Boot default). The easiest way to get both, along with
sensible auto-configuration for the connection, is the Spring Boot starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

You also need a **configured Redis connection** so that Spring Boot provides the `StringRedisTemplate` this repository
uses. See [Spring Boot — Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis) and the
`spring.data.redis.*` properties for how to configure it.

## Wiring it up

With `spring-boot-starter-data-redis` on the classpath and Redis connection properties configured
(`spring.data.redis.*`), Spring Boot provides a `StringRedisTemplate`. Register the repository as a bean and actuator
auditing will use it automatically:

```java
@Bean
AuditEventRepository auditEventRepository(final StringRedisTemplate redisTemplate) {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  return new RedisAuditEventRepository(redisTemplate, "audit:events", eventMapper);
}
```

`"audit:events"` is the Redis key under which the sorted set is stored — choose whatever suits your key naming (for
example prefix it per application). To filter which events are stored, pass a predicate (see
`AbstractAuditEventRepository.inclusionPredicate(...)` / `exclusionPredicate(...)`):

```java
return new RedisAuditEventRepository(redisTemplate, "audit:events", eventMapper,
    AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));
```

## Querying

- **`find(principal, after, type)`** — the `after` bound is served by Redis (`ZREVRANGEBYSCORE`); `principal` and
  `type` are matched in memory. `null` arguments are ignored.
- **`find(Predicate<AuditEvent>)`** — an arbitrary predicate can not be pushed to Redis, so it is evaluated in memory
  over the **most recent** events. That window defaults to 1000 and is configurable via `setMaxFetch(int)`; a warning
  is logged if the limit truncates the result.

Both return events **most recent first**.

## Behavior notes

- **Write failures.** A failure to write to Redis (for example a connection problem) is always logged at `ERROR`. By
  default it additionally throws an `AuditEventWriteException`; call `setThrowOnWriteFail(false)` to only log it and
  continue, so a transient Redis issue does not break the operation that triggered the audit event. Read (`find`)
  failures always propagate.
- **Deduplication.** A sorted set member is unique by its value. Two events that serialize to the exact same JSON
  (same timestamp, principal, type and data) would collapse into a single entry. In practice audit events differ, so
  this is not a concern.

## Retention

Redis does not cap the sorted set — it grows until you prune it. Options:

- set a TTL on the whole key (`redisTemplate.expire(...)`) if you only need a recent window;
- periodically trim by score, e.g. remove everything older than a cutoff:

  ```java
  redisTemplate.opsForZSet().removeRangeByScore("audit:events", 0, cutoff.toEpochMilli());
  ```

## Spring Boot auto-configuration

Auto-configuration (conditional bean creation, plus properties for the key name and fetch limit) is not yet provided —
wire the bean manually as shown above. It is planned as a follow-up.
