![Logo](images/sweden-connect.png)

# Configuration

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

By including the audit support Spring Boot starter as a dependency you get the whole audit chain set up from
properties.

```xml
<dependency>
  <groupId>se.swedenconnect.spring.audit</groupId>
  <artifactId>audit-support-spring-boot-starter</artifactId>
  <version>${audit.support.version}</version>
</dependency>
```

The starter pulls in `audit-support-spring-boot-autoconfigure`, which creates the audit event publication chain and a
Spring Boot
[`AuditEventRepository`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/audit/AuditEventRepository.html)
from the `audit.*` properties. Normally no beans need to be wired by hand.

Every bean is created only if the application has not already declared one of the same type, so you can always take
control by defining your own. The repository auto-configuration additionally backs off entirely if the application
declares an `AuditEventRepository` bean.

<a name="application-name-is-required"></a>
## The application name is required

The audit events produced by this library carry the name of the application that produced them, and the
auto-configuration insists on being able to determine it.

**Set `spring.application.name`.** If it is not set, the artifact from a `BuildProperties` bean is used instead. If
neither is available, the application context fails at startup.

```yaml
spring:
  application:
    name: my-service
```

<a name="actuator"></a>
## Exposing audit events through the actuator

To read audit events through Spring Boot Actuator's `auditevents` endpoint:

- Set `management.auditevents.enabled` to `true`.
- Include `auditevents` in `management.endpoints.web.exposure.include`.
- Make sure an `AuditEventRepository` bean exists, which the auto-configuration provides.

Note that the endpoint can only return what a repository is able to query. Write-only repositories, such as file and
syslog, cannot be read back. See [Repositories](repositories.html#querying).

<a name="what-gets-created"></a>
## What gets created

From `AuditSupportAutoConfiguration`:

- An `ApplicationName` bean, resolved as described above.
- An `AuditEventContextResolver` bean, a `DefaultAuditEventContextResolver`, resolving the application name, the
  correlation ID and trace ID from the [identifier storage](tracing.html), and the principal from the Spring Security
  context.
- An `AuditApplicationListener` bean wired with the resolver and every `EventTransformer` bean found in the context.
- `ApplicationReadyEventTransformer` and `ContextClosedEventTransformer` beans, so that application startup and
  shutdown are audited without any application code. See [Audit Events](audit-events.html) for what they produce. Assign
  `audit.log-lifecycle-events` to `false` to leave the lifecycle unaudited.

From `AuditRepositoryAutoConfiguration`:

- An `AuditEventMapper` bean, a `JsonAuditEventMapper` using the application's Jackson `ObjectMapper`.
- An `AuditEventRepository` bean assembled from the configured repositories, wrapped in a
  `DelegatingAuditEventRepository` that applies the include and exclude filter and the write failure policy once, for
  all of them.
- If no queryable repository is configured, for example only file or syslog, an in-memory repository is added so that
  audit events can still be read. If nothing at all is configured, that in-memory repository is the whole repository.

<a name="repository-order"></a>
### Repository order

Every configured repository receives every event, but a query is answered by the first delegate that returns a result.
The auto-configuration therefore always places the in-memory repository last:

```
file → jdbc → mongo → redis → syslog → in-memory
```

The in-memory repository is a bounded buffer holding only the most recent events. If it came first, an application
configuring both in-memory and a durable store would have every query answered from that small buffer, and the durable
store would never be consulted. The `auditevents` endpoint would silently show only the tail of the audit log. A
configured in-memory repository also satisfies the queryable requirement above, so at most one in-memory repository is
ever created. See [the delegating repository](repositories.html#delegating).

<a name="enabling-a-repository"></a>
## How a repository is enabled

A repository is set up if, and only if, its `audit.repository.<backend>` settings are configured. Every backend has an
`enabled` setting that defaults to `true`, which gives three ways of expressing intent:

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

The `jdbc`, `mongo` and `syslog` backends are also enabled by the application supplying the bean that sets them up, a
`JdbcAuditEventDao`, a `MongoAuditEventDao` or a `SyslogMessageSender`, in which case no properties are needed at all.
Setting `enabled: false` still turns the backend off.

<a name="configuration-properties"></a>
## Configuration properties

### General

| Property | Description | Type | Default value |
| :--- | :--- | :--- | :--- |
| `audit.default-principal` | The principal offered to the event transformers when no user is authenticated. A commonly used value is `system`. | String | - (no principal) |
| `audit.log-lifecycle-events` | Whether the application lifecycle should be audited, i.e., whether a `system_started` event is created when the application has started, and a `system_shutdown` event when it is shutting down. | Boolean | `true` |

<a name="repository-properties"></a>
### Repositories

All of the following live under `audit.repository`. See [Repositories](repositories.html) for what each backend does
and what it requires.

| Property | Description | Type | Default value |
| :--- | :--- | :--- | :--- |
| `in-memory.enabled` | Enable the in-memory repository. | Boolean | `true` |
| `in-memory.capacity` | Number of events to keep in memory. | Integer | Spring default |
| `file.enabled` | Enable the file repository. | Boolean | `true` |
| `file.log-file` | Path to the audit log file, rolled daily. Required. | String | - |
| `jdbc.enabled` | Enable the JDBC repository. Requires `spring-jdbc`, plus a `DataSource` bean or a custom `JdbcAuditEventDao` bean. | Boolean | `true` |
| `jdbc.table-name` | The audit table name. Ignored if a custom `JdbcAuditEventDao` bean is provided. | String | `audit_events` |
| `mongo.enabled` | Enable the MongoDB repository. Requires `spring-data-mongodb`, plus a `MongoTemplate` bean or a custom `MongoAuditEventDao` bean. | Boolean | `true` |
| `mongo.collection` | The MongoDB collection name. Ignored if a custom `MongoAuditEventDao` bean is provided. | String | `audit_events` |
| `redis.enabled` | Enable the Redis repository. Requires `spring-data-redis` and a `StringRedisTemplate` bean. | Boolean | `true` |
| `redis.key` | The Redis sorted set key holding the events. | String | `audit:events` |
| `syslog.enabled` | Enable the syslog repository. Requires `syslog-java-client`. | Boolean | `true` |
| `syslog.host` | Syslog server host. Required whenever the syslog section is configured. | String | - |
| `syslog.port` | Syslog server port. | Integer | `514` |
| `syslog.transport` | `udp` or `tcp`. | String | `udp` |
| `syslog.facility` | Syslog facility, for example `LOCAL0`. | String | `LOCAL0` |
| `syslog.severity` | Syslog severity, for example `INFORMATIONAL`. | String | `INFORMATIONAL` |
| `syslog.message-format` | `RFC_5424` or `RFC_3164`. | String | `RFC_5424` |
| `syslog.app-name` | Syslog application name. | String | `spring.application.name` |
| `include-events[]` | If non-empty, only these event types are logged. | List of strings | empty, meaning all |
| `exclude-events[]` | Event types to exclude. Takes precedence over `include-events`. | List of strings | empty, meaning none |
| `throw-on-write-fail` | Whether a write failure throws an `AuditEventWriteException`. | Boolean | `true` |

> An `enabled` default of `true` only applies to a backend that is present in the configuration. It does not enable
> every backend. An empty `include-events` list means "no inclusion filter", so all events are logged, and **not**
> "include nothing". A type listed in both `include-events` and `exclude-events` is excluded.

The settings of a configured backend are validated when the properties are bound. A missing required setting, a
capacity that is not greater than zero, or an invalid `syslog.transport` or `syslog.port` fails the context at startup.

<a name="optional-dependencies"></a>
## Optional dependencies

Each backend needs its own dependency on the application classpath. The auto-configuration only activates a backend
when its classes, and for JDBC, MongoDB and Redis the required bean, are present:

| Backend | Requires |
| :--- | :--- |
| in-memory, file | Nothing extra |
| jdbc | `spring-jdbc` and a `javax.sql.DataSource` bean, or a custom `JdbcAuditEventDao` bean |
| mongo | `spring-data-mongodb` and a `MongoTemplate` bean, or a custom `MongoAuditEventDao` bean |
| redis | `spring-data-redis` and a `StringRedisTemplate` bean |
| syslog | `com.cloudbees:syslog-java-client` |

If a backend is configured but its requirement is missing, the context fails at startup with a message stating what is
needed, for example *"audit.repository.jdbc is configured, but this requires spring-jdbc on the classpath"*.

> **Redis must be configured.** The Redis repository needs a `StringRedisTemplate`, which Spring Boot creates once you
> configure a Redis connection using the `spring.data.redis.*` properties. See
> [Spring Boot, Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis). Configuring
> `audit.repository.redis` without a configured Redis connection fails at startup.

<a name="examples"></a>
## Examples

Store to a database, and also ship a filtered subset to syslog:

```yaml
spring:
  application:
    name: my-service

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

In-memory only, which is handy during development:

```yaml
audit:
  repository:
    in-memory:
      capacity: 5000
```

<a name="custom-dao"></a>
## Using a custom database DAO

The `jdbc` and `mongo` backends both produce a `DatabaseAuditEventRepository` backed by an
[`AuditEventDao`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/repository/AuditEventDao.java).
By default they use `DefaultJdbcAuditEventDao` or `DefaultMongoAuditEventDao` against the schemas documented under
[Repositories](repositories.html), built from a `DataSource` or `MongoTemplate` and the `table-name` or `collection`
property.

If your schema is different, implement the corresponding **marker** DAO interface, `JdbcAuditEventDao` or
`MongoAuditEventDao`, and declare it as a bean. Such a bean enables the backend on its own, and the
`audit.repository.jdbc` or `audit.repository.mongo` settings are then ignored, with a warning logged if the section is
configured anyway. The marker interface is what lets the JDBC and MongoDB slots tell each other's DAOs apart, so
implement the marker, not the bare `AuditEventDao`. A custom DAO does not require the `DataSource` or `MongoTemplate`
bean, but the backend's library must still be on the classpath.

```java
@Bean
JdbcAuditEventDao auditEventDao(final DataSource dataSource) {
  return new MyOwnJdbcAuditEventDao(dataSource); // your schema, your SQL
}
```

<a name="custom-syslog-sender"></a>
## Using a custom syslog sender

If the application declares its own `SyslogMessageSender` bean, that bean enables the syslog backend and is used as is.
The `audit.repository.syslog.*` properties are then ignored, with a warning logged if the section is configured
alongside the bean. Leave the section out entirely in that case, since a configured section is still validated and
would have to carry a `host` that is never used.

Use a bean when you need TLS or other options not exposed as properties:

```java
@Bean(destroyMethod = "close")
SyslogMessageSender syslogMessageSender() {
  final TcpSyslogMessageSender sender = new TcpSyslogMessageSender();
  sender.setSyslogServerHostname("logs.example.com");
  sender.setSyslogServerPort(6514);
  sender.setSsl(true);
  sender.setMessageFormat(MessageFormat.RFC_5424);
  return sender;
}
```

<a name="customizing-the-audit-event-context"></a>
## Customizing the audit event context

The `AuditEventContext` handed to the event transformers comes from the `AuditEventContextResolver` bean. Declare your
own bean to supply values the default resolver cannot know about, for example a principal taken from something other
than the Spring Security context. The event being audited is passed as input to `getContext(Object)`, so a resolver may
base its result on it.

```java
@Bean
AuditEventContextResolver auditEventContextResolver(final ApplicationName applicationName) {
  return input -> new MyAuditEventContext(applicationName, input);
}
```

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
