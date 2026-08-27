# Syslog Audit Event Repository

`SyslogAuditEventRepository` sends audit events to a syslog server. It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`), so it plugs straight into Spring Boot's actuator auditing.

Each event is serialized to JSON (via the library's `AuditEventMapper`) and sent as the **message body** of a syslog
message.

Syslog is a write-only sink, so **this repository does not support querying** — `supportsFind()` returns `false` and
the `find(...)` methods return an empty list.

## How syslog works in Java

There is no programmatic syslog *sender* in Spring or the JDK. The usual options are the logging frameworks' syslog
appenders (Logback `SyslogAppender`, Log4j2 `SyslogAppender`) or a small client library. This repository uses the
[syslog-java-client](https://github.com/CloudBees-community/syslog-java-client) library, which supports RFC 3164 and
RFC 5424 over UDP, TCP and TCP/TLS.

## Dependency

`syslog-java-client` is an **optional** dependency of this library — declare it in your application:

```xml
<dependency>
  <groupId>com.cloudbees</groupId>
  <artifactId>syslog-java-client</artifactId>
  <version>1.1.7</version>
</dependency>
```

## Wiring it up

You configure a `SyslogMessageSender` (transport, server host/port, facility, severity, message format, application
name) and hand it to the repository. This keeps all syslog protocol concerns in the library where they belong; the
repository only writes the event as the message body.

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

For reliable, secure delivery use `TcpSyslogMessageSender` (optionally with SSL) instead of `UdpSyslogMessageSender`;
the rest is identical.

To filter which events are sent, pass a predicate (see `AbstractAuditEventRepository.inclusionPredicate(...)` /
`exclusionPredicate(...)`):

```java
return new SyslogAuditEventRepository(sender, eventMapper,
    AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));
```

## Behavior notes

- **Write failures.** A failure to send to syslog is always logged at `ERROR`. By default it additionally throws an
  `AuditEventWriteException`; call `setThrowOnWriteFail(false)` to only log it and continue, so a syslog outage does not
  break the operation that triggered the audit event.
- **Sender lifecycle is yours.** `SyslogMessageSender` is `Closeable`. UDP is connectionless, but a
  `TcpSyslogMessageSender` holds a connection — register it so it is closed on shutdown (for example a Spring
  `@Bean(destroyMethod = "close")`).
- **Message size.** UDP syslog messages may be truncated by intermediaries; large audit events are better sent over
  TCP.

## Spring Boot auto-configuration

Auto-configuration (conditional bean creation, plus properties for host/port/transport/facility) is not yet provided —
wire the bean manually as shown above. It is planned as a follow-up.
