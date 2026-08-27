# MongoDB Audit Event Repository

`DatabaseAuditEventRepository` persists audit events to a database via an `AuditEventDao`. This page covers its
**MongoDB** backend (see [jdbc.md](jdbc.md) for relational databases). It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`).

## Design

The same storage-agnostic seam as the JDBC backend — only the DAO differs:

```
DatabaseAuditEventRepository  ──uses──▶  AuditEventDao  ◀──implemented by──  DefaultMongoAuditEventDao
```

- **`AuditEventDao`** — the storage-agnostic seam (`save`, `find(principal, after, type)`, `findRecent(limit)`).
- **`DefaultMongoAuditEventDao implements MongoAuditEventDao`** — the default MongoDB implementation, using Spring
  Data MongoDB's `MongoOperations` (typically a `MongoTemplate`).

**If your document schema is different, implement `MongoAuditEventDao` yourself** and pass it to the repository.

## How events are stored

Each event is one document. A few fields are used for querying; the complete event is stored as JSON (via the
library's `AuditEventMapper`) in `eventData` and is what gets reconstructed on read — so no information is lost.

| Field | Purpose |
|-------|---------|
| `_id` | Mongo-generated document id |
| `eventTime` | event timestamp, stored in **UTC** |
| `principal` | initiator of the event |
| `eventType` | e.g. `system_alert` |
| `applicationName` | from structured events (`null` otherwise) |
| `correlationId` | from structured events (`null` otherwise) |
| `eventData` | the full event serialized as JSON |

No schema or explicit collection creation is required — MongoDB creates the collection on first write. For efficient
`find(principal, after, type)` queries, add indexes on the query fields:

```javascript
db.audit_events.createIndex({ eventTime: -1 });
db.audit_events.createIndex({ principal: 1 });
db.audit_events.createIndex({ eventType: 1 });
```

## Dependencies

`DefaultMongoAuditEventDao` needs `spring-data-mongodb` (an **optional** dependency of this library) plus a Mongo
driver. The easiest way to get both, along with auto-configuration for the connection, is the Spring Boot starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

You also need a **configured MongoDB connection** so that Spring Boot provides the `MongoTemplate` this repository uses.
See [Spring Boot — MongoDB](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.mongodb) and the
`spring.data.mongodb.*` properties.

## Wiring it up

With `spring-boot-starter-data-mongodb` on the classpath and a configured connection, Spring Boot provides a
`MongoTemplate`. Register the repository as a bean and actuator auditing will use it automatically:

```java
@Bean
AuditEventRepository auditEventRepository(final MongoTemplate mongoTemplate) {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  final AuditEventDao dao = new DefaultMongoAuditEventDao(mongoTemplate, eventMapper);
  return new DatabaseAuditEventRepository(dao);
}
```

To filter which events are stored, pass a predicate (see `AbstractAuditEventRepository.inclusionPredicate(...)` /
`exclusionPredicate(...)`):

```java
return new DatabaseAuditEventRepository(dao,
    AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));
```

## Querying

Same two paths as the JDBC backend:

- **`find(principal, after, type)`** — translated to a Mongo query on the flat fields; `null` arguments are ignored.
- **`find(Predicate<AuditEvent>)`** — an arbitrary predicate can not be pushed to Mongo, so it is evaluated in memory
  over the **most recent** events (default 1000, configurable via `setMaxFetch(int)`; a warning is logged on
  truncation).

Both return events **most recent first**.

## Write failures

A failure to write is always logged at `ERROR`. By default it additionally throws an `AuditEventWriteException`; call
`setThrowOnWriteFail(false)` to only log it and continue.

## Custom schema

If your document layout is different, implement `MongoAuditEventDao` and use it directly:

```java
public class MyMongoAuditEventDao implements MongoAuditEventDao {
  // your own MongoOperations / query / document mapping ...
}

return new DatabaseAuditEventRepository(new MyMongoAuditEventDao(...));
```

When using the Spring Boot starter, just declare your `MongoAuditEventDao` as a bean and it is picked up automatically
(see [autoconfigure.md](autoconfigure.md)).

## Retention

The collection grows until you prune it. Options include a
[TTL index](https://www.mongodb.com/docs/manual/core/index-ttl/) on `eventTime`, or a scheduled delete:

```javascript
db.audit_events.deleteMany({ eventTime: { $lt: cutoff } });
```

## Spring Boot auto-configuration

With the starter, set `audit.repository.mongo.enabled=true` and provide a `MongoTemplate` (or a custom
`MongoAuditEventDao` bean) — the repository is created for you. See [autoconfigure.md](autoconfigure.md).
