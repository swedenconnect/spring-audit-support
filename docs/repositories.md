![Logo](images/sweden-connect.png)

# Audit Event Repositories

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

A repository is where audit events end up. This page covers the repositories the library supplies, what each of them
requires, and how to wire one by hand.

Under the Spring Boot starter you normally do not wire anything. The repositories are created from properties, see
[Configuration](configuration.html#repository-properties). The manual wiring shown here is for applications not using
the starter, and for when a repository needs something the properties do not expose.

<a name="the-repository-interface"></a>
## The repository interface

Every repository implements
[`ExtendedAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java),
which extends Spring Boot's `AuditEventRepository`. So they plug straight into the actuator's auditing, while adding
event filtering and a richer query API.

Most repositories extend
[`AbstractAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/AbstractAuditEventRepository.java),
which supplies two things common to all of them, described next.

<a name="filtering"></a>
### Filtering

Each repository may be given a predicate deciding which events it stores. Build one with the static helpers on
`AbstractAuditEventRepository`:

```java
AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type"));
AbstractAuditEventRepository.inclusionPredicate(List.of("user_login", "user_logout"));
AbstractAuditEventRepository.inclusionExclusionPredicate(includeTypes, excludeTypes);
```

Note the difference between the first two and the third. `inclusionPredicate` is a literal whitelist, so an empty list
accepts nothing. `inclusionExclusionPredicate` is meant for configuration, where an unset include list means "no
inclusion constraint", so an empty include list accepts everything not excluded. Exclusion always wins over inclusion.

When several repositories are combined, put the filter on the [delegating repository](#delegating) instead of on each
one, so the rule is stated once.

<a name="write-failures"></a>
### Write failures

A failure to write is always logged at `ERROR`. By default it additionally throws an `AuditEventWriteException`. Call
`setThrowOnWriteFail(false)` to log and continue, so that a storage problem does not break the operation that triggered
the audit event.

Which is right depends on the deployment. Throwing means an audit event is never silently lost. Continuing means a
failing sink cannot take the service down. A common arrangement is to throw for the durable store and continue for the
rest, which the [delegating repository](#delegating) supports per delegate.

Read failures always propagate.

<a name="querying"></a>
### Querying

Two query paths exist:

- **`find(principal, after, type)`**, from Spring Boot's own interface. Where the backend can, this is pushed down to
  the store. `null` arguments are ignored.
- **`find(Predicate<AuditEvent>)`**, an arbitrary predicate. This cannot be pushed down to any of the backends, so it
  is evaluated in memory over the most recent events. The window defaults to 1000 and is set with `setMaxFetch(int)`. A
  warning is logged if the limit truncates the result.

Both return events most recent first.

Not every repository can be queried. `supportsFind()` reports whether predicate based queries can be served. The file
and syslog repositories are write-only sinks, so they return `false` and their `find` methods return an empty list.

| Repository | Queryable |
| :--- | :--- |
| In-memory | Yes |
| JDBC | Yes |
| MongoDB | Yes |
| Redis | Yes |
| File | No |
| Syslog | No |

<a name="in-memory"></a>
## In-memory

[`InMemoryAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/InMemoryAuditEventRepository.java)
keeps a bounded number of the most recent events in memory. Everything is lost when the application stops, so it is for
development, for tests, and for making the actuator endpoint useful in an application whose only other repository is
write-only.

It needs no dependencies and no configuration.

```java
@Bean
AuditEventRepository auditEventRepository() {
  return new InMemoryAuditEventRepository(5000);
}
```

Because its window is small, it should always come last when combined with a durable repository. See
[Configuration](configuration.html#repository-order).

<a name="file"></a>
## File

[`FileBasedAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/FileBasedAuditEventRepository.java)
writes events to a file, one JSON event per line. It uses only the JDK, so no extra dependency is needed.

It is write-only and does not support querying.

### Daily file rolling

The file is rolled per date in UTC. When the first event of a new day is written, the current file is renamed to
`<name>-<yyyyMMdd>.<ext>` and a fresh file is started:

```
audit.log              <- today's events
audit-20260806.log     <- yesterday's events
audit-20260805.log
```

If the file name has no extension, the date is appended, so `audit` becomes `audit-20260806`.

Rolled files accumulate indefinitely. Prune them with your normal log retention tooling.

### Wiring it up

The constructor throws `IOException` if the path is invalid, meaning it points to a directory or to an existing file
that is not writable. Missing parent directories are created.

```java
@Bean
AuditEventRepository auditEventRepository() throws IOException {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  return new FileBasedAuditEventRepository("/var/log/myapp/audit.log", eventMapper);
}
```

> **One writer per file.** The repository backs the file with a Java Util Logging `FileHandler`, which also creates a
> `<name>.lck` lock file next to it. Use a single `FileBasedAuditEventRepository` instance per file within the JVM.

<a name="jdbc"></a>
## JDBC

[`DatabaseAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/DatabaseAuditEventRepository.java)
persists events to a database through an `AuditEventDao`. It contains no SQL and no schema knowledge of its own:

```
DatabaseAuditEventRepository  ──uses──▶  AuditEventDao  ◀──implemented by──  DefaultJdbcAuditEventDao
   (filtering, predicate find,             (the seam)                          (the default schema and SQL)
    ordering)
```

`AuditEventDao` has three operations expressed purely in terms of `AuditEvent`: `save`, `find(principal, after, type)`
and `findRecent(limit)`. Two marker sub-interfaces, `JdbcAuditEventDao` and `MongoAuditEventDao`, let the
auto-configuration tell the two database backends apart.

### How events are stored

Each event is one row. A few flat columns are used for querying, and the complete event is stored as JSON in
`event_data`, which is what gets reconstructed on read. No information is lost, including nested `data` and extra root
fields.

| Column | Purpose |
| :--- | :--- |
| `id` | Auto-generated primary key, also the tiebreaker for ordering |
| `event_time` | Event timestamp, stored in UTC |
| `principal` | Initiator of the event |
| `event_type` | For example `system_alert` |
| `application_name` | From structured events, `null` otherwise |
| `correlation_id` | From structured events, `null` otherwise |
| `event_data` | The full event serialized as JSON |

Note that the trace ID has no flat column. It is preserved in `event_data` like everything else, but it cannot be used
as a query criterion.

### Dependencies

`DefaultJdbcAuditEventDao` needs `spring-jdbc`, an optional dependency of this library, and a JDBC driver for your
database:

```xml
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-jdbc</artifactId>
</dependency>

<!-- Your database driver, for example PostgreSQL -->
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
</dependency>
```

A custom `AuditEventDao` that does not use `JdbcTemplate` needs neither.

### Schema

The table name defaults to `audit_events` and is configurable. The column names are fixed. The indexes are optional but
recommended, since they back the `find(principal, after, type)` query.

**PostgreSQL**

```sql
CREATE TABLE audit_events (
    id               BIGSERIAL PRIMARY KEY,
    event_time       TIMESTAMP NOT NULL,
    principal        VARCHAR(255),
    event_type       VARCHAR(255) NOT NULL,
    application_name VARCHAR(255),
    correlation_id   VARCHAR(255),
    event_data       TEXT NOT NULL
);

CREATE INDEX idx_audit_events_time      ON audit_events (event_time);
CREATE INDEX idx_audit_events_principal ON audit_events (principal);
CREATE INDEX idx_audit_events_type      ON audit_events (event_type);
```

**MySQL and MariaDB**

```sql
CREATE TABLE audit_events (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_time       DATETIME(6) NOT NULL,
    principal        VARCHAR(255),
    event_type       VARCHAR(255) NOT NULL,
    application_name VARCHAR(255),
    correlation_id   VARCHAR(255),
    event_data       TEXT NOT NULL
);

CREATE INDEX idx_audit_events_time      ON audit_events (event_time);
CREATE INDEX idx_audit_events_principal ON audit_events (principal);
CREATE INDEX idx_audit_events_type      ON audit_events (event_type);
```

`DATETIME(6)` is used rather than `TIMESTAMP` to avoid MySQL's year 2038 range limit and to keep microsecond precision.

**H2, for testing**

```sql
CREATE TABLE audit_events (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_time       TIMESTAMP NOT NULL,
    principal        VARCHAR(255),
    event_type       VARCHAR(255) NOT NULL,
    application_name VARCHAR(255),
    correlation_id   VARCHAR(255),
    event_data       CLOB NOT NULL
);
```

### Wiring it up

```java
@Bean
AuditEventRepository auditEventRepository(final DataSource dataSource) {
  final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  final AuditEventDao dao = new DefaultJdbcAuditEventDao(jdbcTemplate, eventMapper);
  return new DatabaseAuditEventRepository(dao);
}
```

If your table looks different, implement `JdbcAuditEventDao` and pass it to the same repository. See
[Configuration](configuration.html#custom-dao) for doing this under the starter.

### Retention

The table grows until you prune it:

```sql
DELETE FROM audit_events WHERE event_time < ?; -- for example older than 90 days
```

Database-native partitioning or time-to-live features work as well.

<a name="mongodb"></a>
## MongoDB

The same `DatabaseAuditEventRepository` as above, with `DefaultMongoAuditEventDao` as the DAO. It uses Spring Data
MongoDB's `MongoOperations`, typically a `MongoTemplate`.

### How events are stored

Each event is one document, following the same principle as the JDBC backend: flat fields for querying, the complete
event as JSON in `eventData`.

| Field | Purpose |
| :--- | :--- |
| `_id` | Mongo-generated document id |
| `eventTime` | Event timestamp, stored in UTC |
| `principal` | Initiator of the event |
| `eventType` | For example `system_alert` |
| `applicationName` | From structured events, `null` otherwise |
| `correlationId` | From structured events, `null` otherwise |
| `eventData` | The full event serialized as JSON |

No schema or explicit collection creation is needed, since MongoDB creates the collection on first write. For efficient
`find(principal, after, type)` queries, add indexes on the query fields:

```javascript
db.audit_events.createIndex({ eventTime: -1 });
db.audit_events.createIndex({ principal: 1 });
db.audit_events.createIndex({ eventType: 1 });
```

### Dependencies

`spring-data-mongodb`, an optional dependency of this library, plus a Mongo driver. The Spring Boot starter gives you
both along with connection auto-configuration:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

You also need a configured MongoDB connection so that Spring Boot provides the `MongoTemplate`. See
[Spring Boot, MongoDB](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.mongodb) and the
`spring.data.mongodb.*` properties.

### Wiring it up

```java
@Bean
AuditEventRepository auditEventRepository(final MongoTemplate mongoTemplate) {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  final AuditEventDao dao = new DefaultMongoAuditEventDao(mongoTemplate, eventMapper);
  return new DatabaseAuditEventRepository(dao);
}
```

If your document layout is different, implement `MongoAuditEventDao` and pass it to the repository.

### Retention

The collection grows until you prune it. Use a
[TTL index](https://www.mongodb.com/docs/manual/core/index-ttl/) on `eventTime`, or a scheduled delete:

```javascript
db.audit_events.deleteMany({ eventTime: { $lt: cutoff } });
```

<a name="redis"></a>
## Redis

[`RedisAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/RedisAuditEventRepository.java)
persists events to Redis using [Spring Data Redis](https://spring.io/projects/spring-data-redis).

### Storage model

Events are stored in a Redis sorted set:

- the **member** is the complete event serialized as JSON;
- the **score** is the event timestamp in epoch milliseconds.

Scoring by timestamp gives time-ordered storage for free, and lets `find(principal, after, type)` push the `after`
criterion down to Redis as a `ZREVRANGEBYSCORE` query. `principal` and `type` are then matched in memory. Because the
whole event is stored as JSON and reconstructed on read, no information is lost.

> Redis does have a dedicated time-series type, but only through the RedisTimeSeries module in Redis Stack, which
> Spring Data Redis does not wrap. The sorted set approach gives equivalent time-range querying using core Redis.

> **A sorted set member is unique by its value.** Two events serializing to exactly the same JSON, meaning the same
> timestamp, principal, type and data, collapse into a single entry. In practice audit events differ, so this is rarely
> a concern, but it is a property of the storage model rather than something the repository can prevent.

### Dependencies

`spring-data-redis`, an optional dependency of this library, plus a Redis client, where Lettuce is the Spring Boot
default. The Spring Boot starter gives you both:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

You also need a configured Redis connection so that Spring Boot provides the `StringRedisTemplate`. See
[Spring Boot, Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis) and the
`spring.data.redis.*` properties.

### Wiring it up

```java
@Bean
AuditEventRepository auditEventRepository(final StringRedisTemplate redisTemplate) {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  return new RedisAuditEventRepository(redisTemplate, "audit:events", eventMapper);
}
```

`audit:events` is the key under which the sorted set is stored. Choose whatever suits your key naming, for example a
per-application prefix.

### Retention

Redis does not cap the sorted set. Either set a TTL on the whole key with `redisTemplate.expire(...)` if you only need
a recent window, or trim by score periodically:

```java
redisTemplate.opsForZSet().removeRangeByScore("audit:events", 0, cutoff.toEpochMilli());
```

<a name="syslog"></a>
## Syslog

[`SyslogAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/SyslogAuditEventRepository.java)
sends each event, serialized as JSON, as the message body of a syslog message.

Syslog is a write-only sink, so this repository does not support querying.

### How syslog works in Java

There is no programmatic syslog sender in Spring or the JDK. The usual options are the logging frameworks' syslog
appenders, Logback's or Log4j2's `SyslogAppender`, or a small client library. This repository uses
[syslog-java-client](https://github.com/CloudBees-community/syslog-java-client), which supports RFC 3164 and RFC 5424
over UDP, TCP and TCP with TLS.

### Dependency

`syslog-java-client` is an optional dependency of this library. Declare it in your application:

```xml
<dependency>
  <groupId>com.cloudbees</groupId>
  <artifactId>syslog-java-client</artifactId>
  <version>1.1.7</version>
</dependency>
```

### Wiring it up

You configure a `SyslogMessageSender`, covering transport, host and port, facility, severity, message format and
application name, and hand it to the repository. All syslog protocol concerns stay in the sender. The repository only
writes the event as the message body.

```java
@Bean
AuditEventRepository auditEventRepository() {
  final UdpSyslogMessageSender sender = new UdpSyslogMessageSender();
  sender.setSyslogServerHostname("logs.example.com");
  sender.setSyslogServerPort(514);
  sender.setDefaultAppName("my-service");
  sender.setDefaultFacility(Facility.LOCAL0);
  sender.setDefaultSeverity(Severity.INFORMATIONAL);
  sender.setMessageFormat(MessageFormat.RFC_5424);

  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  return new SyslogAuditEventRepository(sender, eventMapper);
}
```

For reliable, secure delivery use `TcpSyslogMessageSender`, optionally with SSL. The rest is identical.

> **The sender lifecycle is yours.** `SyslogMessageSender` is `Closeable`. UDP is connectionless, but a
> `TcpSyslogMessageSender` holds a connection, so register it as `@Bean(destroyMethod = "close")`.

> **Message size.** UDP syslog messages may be truncated by intermediaries. Large audit events are better sent over TCP.

<a name="delegating"></a>
## The delegating repository

[`DelegatingAuditEventRepository`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/DelegatingAuditEventRepository.java)
forwards events to several repositories at once, for example to persist to a database and also ship to syslog. This is
what the auto-configuration builds when more than one backend is configured.

- **`add(event)`** applies this repository's own filter and forwards accepted events to all delegates.
- **`find(principal, after, type)`** and **`find(criteria)`** both try each delegate in order and return the first
  non-empty result. Results are never merged. They differ in which delegates they can use: `find(principal, after,
  type)` uses all delegates, since every `AuditEventRepository` offers that method, while `find(criteria)` only uses
  delegates that are `ExtendedAuditEventRepository` instances supporting find.
- **`supportsFind()`** is `true` if at least one delegate is an `ExtendedAuditEventRepository` whose `supportsFind()`
  is `true`, so it reports whether a predicate based query can be served. When it is `false`, `find(criteria)` returns
  an empty list. A plain `AuditEventRepository` delegate does not make it `true`, even though such a delegate can still
  answer `find(principal, after, type)`.

### Delegate order matters

Since a query is answered by the first delegate returning a result, list the delegates in the order you want them
consulted: the most complete store first, the most limited one last. In particular, an in-memory repository should
always come last, for the reason given in [Configuration](configuration.html#repository-order).

### Filtering

Configure the event filter on the delegating repository, not on the individual delegates. Filtering there is applied
once, consistently, for every delegate, whereas per-delegate filters are easy to get out of sync.

### Write failure handling

Every delegate is attempted even if an earlier one fails, and each failure is logged. Whether the overall `add(...)`
then throws is resolved per delegate:

| Delegate's `throwOnWriteFail` | Result on that delegate's failure |
| :--- | :--- |
| Explicitly `true` | Throw. The delegate's choice wins. |
| Explicitly `false` | Log only. The delegate's choice wins. |
| Unset | Inherit the delegating repository's `setThrowOnWriteFail(...)` value, which itself defaults to throwing. |

If, after all delegates have been attempted, any failed delegate's resolved value was "throw", the delegating
repository throws an `AuditEventWriteException`. The first failure is thrown and the rest are attached as suppressed
exceptions.

Set the policy once on the delegating repository, and override it on a specific delegate only when that delegate needs
different behaviour, for example "the local file sink must never break the request, but a failure to write to the
database should".

### Wiring it up

```java
@Bean
AuditEventRepository auditEventRepository(final DatabaseAuditEventRepository database,
    final SyslogAuditEventRepository syslog) {

  final DelegatingAuditEventRepository repository =
      new DelegatingAuditEventRepository(List.of(database, syslog),
          AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));

  // Best effort: a failing sink should not break the audited operation.
  repository.setThrowOnWriteFail(false);
  return repository;
}
```

Because the filter is on the delegating repository, the two delegates above should be created without filters of their
own.

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
