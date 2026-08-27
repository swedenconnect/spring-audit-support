# Auto-configuration

The `audit-support-spring-boot-starter` (which pulls in `audit-support-spring-boot-autoconfigure`) automatically sets
up the audit event publication chain and a Spring Boot
[`AuditEventRepository`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/audit/AuditEventRepository.html)
based on `audit.*` properties, so you normally don't wire any beans yourself.

Every bean is created only if the application has not already declared one of the same type, so you can always take
full control by defining your own. The whole repository auto-configuration additionally **backs off** if the
application declares an `AuditEventRepository` bean.

## What gets created

From `AuditSupportAutoConfiguration`:

- An `ApplicationName` bean, resolved from `spring.application.name`, or the artifact of the `BuildProperties` bean if
  that property is not set. If neither is available, the context fails at startup.
- An `AuditEventContextResolver` bean (a `DefaultAuditEventContextResolver`) that resolves the application name, the
  correlation ID from MDC, and the principal from the Spring Security context.
- An `AuditApplicationListener` bean wired with the resolver and every `EventTransformer` bean found in the context.

From `AuditRepositoryAutoConfiguration`:

- An `AuditEventMapper` bean (a `JsonAuditEventMapper` using the application's Jackson `ObjectMapper`), unless one is
  already defined.
- An `AuditEventRepository` bean assembled from the configured repositories. They are wrapped in a
  `DelegatingAuditEventRepository`, which applies the include/exclude filter and the write-failure policy once, for all
  of them.
- If no *queryable* repository is configured (for example only file or syslog, which are write-only), an in-memory
  repository is added so that audit events can still be read — e.g. via the actuator `auditevents` endpoint. If nothing
  at all is configured, that in-memory repository is the whole repository.

### Repository order

Every configured repository receives every event, but a **query** is answered by the first delegate that returns a
result (see [delegating.md](delegating.md)). The auto-configuration therefore always places the in-memory repository
**last**:

```
file → jdbc → mongo → redis → syslog → in-memory
```

The in-memory repository is a bounded buffer holding only the most recent events. If it came first, an application
that configures both in-memory and a durable store would have every query answered from that small buffer, and the
durable store would never be consulted — the `auditevents` endpoint would silently show only the tail of the audit log.
A configured in-memory repository also satisfies the "queryable" requirement above, so at most one in-memory repository
is ever created.

## How a repository is enabled

A repository is set up if, and only if, its `audit.repository.<backend>` settings are configured. Every backend has an
`enabled` setting that **defaults to `true`**, which gives three ways of expressing intent:

```yaml
audit:
  repository:
    file:
      log-file: /var/log/audit.log   # configuring any setting enables the backend
    redis:
      enabled: true                  # for a backend whose settings all have defaults
    syslog:
      enabled: false                 # keep the settings, but turn the backend off
      host: logs.example.com
```

The settings of a backend that is not enabled are neither validated nor used.

The `jdbc`, `mongo` and `syslog` backends are *also* enabled by the application supplying the bean that sets them up —
a `JdbcAuditEventDao`, a `MongoAuditEventDao` or a `SyslogMessageSender` — in which case no properties are needed at
all. Setting `enabled: false` still turns the backend off.

## Properties

### General

| Property | Description | Type | Default |
| :--- | :--- | :--- | :--- |
| `audit.default-principal` | The principal offered to the event transformers when no user is authenticated. A commonly used value is `system`. | String | - (no principal) |

### Repositories

All of the following live under `audit.repository`.

| Property | Description | Type | Default |
| :--- | :--- | :--- | :--- |
| `in-memory.enabled` | Enable the in-memory repository. | boolean | `true` |
| `in-memory.capacity` | Number of events to keep in memory. | Integer | Spring default |
| `file.enabled` | Enable the file repository. | boolean | `true` |
| `file.log-file` | Path to the audit log file (rolled daily). **Required.** | String | - |
| `jdbc.enabled` | Enable the JDBC (relational database) repository. Requires `spring-jdbc`, plus a `DataSource` bean or a custom `JdbcAuditEventDao` bean. | boolean | `true` |
| `jdbc.table-name` | The audit table name (ignored if a custom `JdbcAuditEventDao` bean is provided). | String | `audit_events` |
| `mongo.enabled` | Enable the MongoDB repository. Requires `spring-data-mongodb`, plus a `MongoTemplate` bean or a custom `MongoAuditEventDao` bean. | boolean | `true` |
| `mongo.collection` | The MongoDB collection name (ignored if a custom `MongoAuditEventDao` bean is provided). | String | `audit_events` |
| `redis.enabled` | Enable the Redis repository. Requires a `StringRedisTemplate` bean and `spring-data-redis`. | boolean | `true` |
| `redis.key` | The Redis sorted-set key holding the events. | String | `audit:events` |
| `syslog.enabled` | Enable the syslog repository. Requires `syslog-java-client`. | boolean | `true` |
| `syslog.host` | Syslog server host. **Required** whenever the syslog section is configured. | String | - |
| `syslog.port` | Syslog server port. | Integer | `514` |
| `syslog.transport` | `udp` or `tcp`. | String | `udp` |
| `syslog.facility` | Syslog facility, e.g. `LOCAL0`. | String | `LOCAL0` |
| `syslog.severity` | Syslog severity, e.g. `INFORMATIONAL`. | String | `INFORMATIONAL` |
| `syslog.message-format` | `RFC_5424` or `RFC_3164`. | String | `RFC_5424` |
| `syslog.app-name` | Syslog application name. | String | `spring.application.name` |
| `include-events[]` | If non-empty, only these event types are logged. | List of strings | empty (all) |
| `exclude-events[]` | Event types to exclude. Takes precedence over `include-events`. | List of strings | empty (none) |
| `throw-on-write-fail` | Whether a write failure throws an `AuditEventWriteException`. | Boolean | `true` |

> Remember that an `enabled` default of `true` only applies to a backend that is present in the configuration — it does
> not enable every backend. An empty `include-events` list means "no inclusion filter" (all events are logged),
> **not** "include nothing". A type listed in both `include-events` and `exclude-events` is excluded.

The settings of a configured backend are validated when the properties are bound: a missing required setting, a
capacity that is not greater than zero, or an invalid `syslog.transport` / `syslog.port` fails the context at startup.

## Optional dependencies

Each backend needs its own dependency on the application classpath; the auto-configuration only activates a backend
when its classes (and, for JDBC/Mongo/Redis, the required bean) are present:

| Backend | Requires |
| :--- | :--- |
| in-memory, file | nothing extra |
| jdbc | `spring-jdbc` + a `javax.sql.DataSource` bean (or a custom `JdbcAuditEventDao` bean) |
| mongo | `spring-data-mongodb` + a `MongoTemplate` bean (or a custom `MongoAuditEventDao` bean) |
| redis | `spring-data-redis` + a `StringRedisTemplate` bean |
| syslog | `com.cloudbees:syslog-java-client` |

If a backend is configured but its requirement is missing, the context fails at startup with a message telling you what
is needed — for example *"audit.repository.jdbc is configured, but this requires spring-jdbc on the classpath"*.

> **Redis must be configured.** The Redis repository needs a `StringRedisTemplate`, which Spring Boot creates once you
> configure a Redis connection (the `spring.data.redis.*` properties). See
> [Spring Boot — Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis) for how to set
> that up. Configuring `audit.repository.redis` without a configured Redis connection fails at startup.

## Examples

Store to a database, and also ship a filtered subset to syslog:

```yaml
audit:
  default-principal: system
  repository:
    jdbc:
      table-name: audit_events
    syslog:
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
and declare it as a **bean**. Such a bean enables the backend on its own, and the `audit.repository.jdbc` /
`audit.repository.mongo` settings are then ignored (a warning is logged if the section is configured anyway). The
marker interface is what lets the JDBC and MongoDB slots tell each other's DAOs apart, so **implement the marker, not
the bare `AuditEventDao`**. A custom DAO does not require the `DataSource`/`MongoTemplate` bean, but the backend's
library (`spring-jdbc` / `spring-data-mongodb`) must still be on the classpath.

```java
@Bean
JdbcAuditEventDao auditEventDao(final DataSource dataSource) {
  return new MyOwnJdbcAuditEventDao(dataSource); // your schema, your SQL
}
```

## Syslog: bean vs. properties

If the application declares its own `SyslogMessageSender` bean, that bean enables the syslog backend and is used as-is
— the `audit.repository.syslog.*` properties are then **ignored** (a warning is logged if the section is configured
alongside the bean). Leave the section out entirely in that case: a configured section is still validated, so it would
have to carry a `host` that is never used. Without a bean, a `SyslogMessageSender` is built from the properties above.
Use a bean when you need TLS or other options not exposed as properties:

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

## Customizing the audit event context

The `AuditEventContext` handed to the event transformers comes from the `AuditEventContextResolver` bean. Declare your
own bean to supply values that the default resolver cannot know about — a trace ID from your tracing framework, or a
principal taken from something other than the Spring Security context. The event being audited is passed as input to
`getContext(Object)`, so a resolver may base its result on it.

```java
@Bean
AuditEventContextResolver auditEventContextResolver(final ApplicationName applicationName) {
  return input -> new MyAuditEventContext(applicationName, input);
}
```

See the per-backend pages for details: [jdbc](jdbc.md), [mongo](mongo.md), [redis](redis.md), [syslog](syslog.md), [file](file.md),
[delegating](delegating.md).
