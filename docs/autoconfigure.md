# Auto-configuration

The `audit-support-spring-boot-starter` (which pulls in `audit-support-spring-boot-autoconfigure`) automatically sets
up a Spring Boot [`AuditEventRepository`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/audit/AuditEventRepository.html)
based on `audit.repository.*` properties, so you normally don't wire any repository beans yourself.

The whole repository auto-configuration **backs off** if the application already declares an `AuditEventRepository`
bean, so you can always take full control by defining your own.

## What gets created

- An `AuditEventMapper` bean (a `JsonAuditEventMapper` using the application's Jackson `ObjectMapper`), unless one is
  already defined.
- An `AuditEventRepository` bean assembled from the enabled repositories. When more than one is enabled they are wrapped
  in a `DelegatingAuditEventRepository` (which applies the include/exclude filter and the write-failure policy once, for
  all of them).
- If no *queryable* repository is enabled (for example only file or syslog, which are write-only), an in-memory
  repository is added so that audit events can still be read — e.g. via the actuator `auditevents` endpoint. If nothing
  at all is configured, that in-memory repository is the whole repository.

## Properties

All properties live under `audit.repository`.

| Property | Description | Type | Default |
| :--- | :--- | :--- | :--- |
| `in-memory.enabled` | Enable the in-memory repository. | boolean | `false` |
| `in-memory.capacity` | Number of events to keep in memory. | Integer | Spring default |
| `file.enabled` | Enable the file repository. | boolean | `false` |
| `file.log-file` | Path to the audit log file (rolled daily). | String | - |
| `jdbc.enabled` | Enable the JDBC (relational database) repository. Requires `spring-jdbc`, plus a `DataSource` bean or a custom `JdbcAuditEventDao` bean. | boolean | `false` |
| `jdbc.table-name` | The audit table name (ignored if a custom `JdbcAuditEventDao` bean is provided). | String | `audit_events` |
| `mongo.enabled` | Enable the MongoDB repository. Requires `spring-data-mongodb`, plus a `MongoTemplate` bean or a custom `MongoAuditEventDao` bean. | boolean | `false` |
| `mongo.collection` | The MongoDB collection name (ignored if a custom `MongoAuditEventDao` bean is provided). | String | `audit_events` |
| `redis.enabled` | Enable the Redis repository. Requires a `StringRedisTemplate` bean and `spring-data-redis`. | boolean | `false` |
| `redis.key` | The Redis sorted-set key holding the events. | String | `audit:events` |
| `syslog.enabled` | Enable the syslog repository. Requires `syslog-java-client`. | boolean | `false` |
| `syslog.host` | Syslog server host. | String | - |
| `syslog.port` | Syslog server port. | Integer | `514` |
| `syslog.transport` | `udp` or `tcp`. | String | `udp` |
| `syslog.facility` | Syslog facility, e.g. `LOCAL0`. | String | `LOCAL0` |
| `syslog.severity` | Syslog severity, e.g. `INFORMATIONAL`. | String | `INFORMATIONAL` |
| `syslog.message-format` | `RFC_5424` or `RFC_3164`. | String | `RFC_5424` |
| `syslog.app-name` | Syslog application name. | String | `spring.application.name` |
| `include-events[]` | If non-empty, only these event types are logged. | List of strings | empty (all) |
| `exclude-events[]` | Event types to exclude. | List of strings | empty (none) |
| `throw-on-write-fail` | Whether a write failure throws an `AuditEventWriteException`. | Boolean | `true` |

> An empty `include-events` list means "no inclusion filter" (all events are logged), **not** "include nothing".

## Optional dependencies

Each backend needs its own dependency on the application classpath; the auto-configuration only activates a backend
when its classes (and, for JDBC/Redis, the required bean) are present:

| Backend | Requires |
| :--- | :--- |
| in-memory, file | nothing extra |
| jdbc | `spring-jdbc` + a `javax.sql.DataSource` bean (or a custom `JdbcAuditEventDao` bean) |
| mongo | `spring-data-mongodb` + a `MongoTemplate` bean (or a custom `MongoAuditEventDao` bean) |
| redis | `spring-data-redis` + a `StringRedisTemplate` bean |
| syslog | `com.cloudbees:syslog-java-client` |

If a backend is `enabled=true` but its requirement is missing, the context fails at startup with a message telling you
what is needed.

> **Redis must be configured.** The Redis repository needs a `StringRedisTemplate`, which Spring Boot creates once you
> configure a Redis connection (the `spring.data.redis.*` properties). See
> [Spring Boot — Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis) for how to set
> that up. Enabling `audit.repository.redis` without a configured Redis connection fails at startup.

## Examples

Store to a database, and also ship a filtered subset to syslog:

```yaml
audit:
  repository:
    jdbc:
      enabled: true
      table-name: audit_events
    syslog:
      enabled: true
      host: logs.example.com
      transport: tcp
    exclude-events:
      - noisy_event_type
    throw-on-write-fail: false
```

In-memory only (handy for development):

```yaml
audit:
  repository:
    in-memory:
      enabled: true
      capacity: 5000
```

## Database: default schema vs. a custom DAO

The `jdbc` and `mongo` backends both produce a `DatabaseAuditEventRepository` backed by an
[`AuditEventDao`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/AuditEventDao.java). By
default they use `DefaultJdbcAuditEventDao` / `DefaultMongoAuditEventDao` against the schemas documented in
[jdbc.md](jdbc.md) / [mongo.md](mongo.md), built from a `DataSource`/`MongoTemplate` and the `table-name`/`collection`
property.

If your schema is different, implement the corresponding **marker** DAO interface —
[`JdbcAuditEventDao`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/JdbcAuditEventDao.java) or
[`MongoAuditEventDao`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/MongoAuditEventDao.java) —
and declare it as a **bean**. The auto-configuration uses it (and ignores the `table-name`/`collection` property,
logging a warning if it is also set). The marker interface is what lets the JDBC and MongoDB slots tell each other's
DAOs apart, so **implement the marker, not the bare `AuditEventDao`**. A custom DAO does not require the
`DataSource`/`MongoTemplate` bean, but the backend's library (`spring-jdbc` / `spring-data-mongodb`) must still be on
the classpath.

```java
@Bean
JdbcAuditEventDao auditEventDao(final DataSource dataSource) {
  return new MyOwnJdbcAuditEventDao(dataSource); // your schema, your SQL
}
```

## Syslog: bean vs. properties

If the application declares its own `SyslogMessageSender` bean, that bean is used and the `audit.repository.syslog.*`
properties (other than `enabled`) are **ignored** — a warning is logged if such properties are set alongside a bean.
Otherwise a `SyslogMessageSender` is built from the properties above. Use a bean when you need TLS or other options not
exposed as properties:

```java
@Bean
SyslogMessageSender syslogMessageSender() {
  final TcpSyslogMessageSender sender = new TcpSyslogMessageSender();
  sender.setSyslogServerHostname("logs.example.com");
  sender.setSyslogServerPort(6514);
  sender.setSsl(true);
  sender.setMessageFormat(MessageFormat.RFC_5424);
  return sender;
}
```

See the per-backend pages for details: [jdbc](jdbc.md), [mongo](mongo.md), [redis](redis.md), [syslog](syslog.md), [file](file.md),
[delegating](delegating.md).
