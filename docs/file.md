# File-based Audit Event Repository

`FileBasedAuditEventRepository` writes audit events to a file, one JSON event per line. It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`), so it plugs straight into Spring Boot's actuator auditing.

Each event is serialized to JSON (via the library's `AuditEventMapper`) and appended as a single line.

Being write-only, this repository does **not** support querying — `supportsFind()` returns `false` and the `find(...)`
methods return an empty list.

## Dependencies

None beyond the core library. It uses only the JDK (Java Util Logging + `java.nio`).

## Daily file rolling

The file is rolled per date (UTC). When the first event of a new day is written, the current file is renamed to
`<name>-<yyyyMMdd>.<ext>` and a fresh file is started. For example, with a log file `audit.log`:

```
audit.log              <- today's events
audit-20260806.log     <- yesterday's events
audit-20260805.log     <- ...
```

If the file name has no extension, the date is simply appended (`audit` → `audit-20260806`).

## Wiring it up

Register the repository as a bean; actuator auditing will use it automatically. The constructor throws `IOException` if
the path is invalid (points to a directory, or an existing file that is not writable); missing parent directories are
created.

```java
@Bean
AuditEventRepository auditEventRepository() throws IOException {
  final AuditEventMapper eventMapper = new JsonAuditEventMapper(JsonMapper.builder().build());
  return new FileBasedAuditEventRepository("/var/log/myapp/audit.log", eventMapper);
}
```

To filter which events are written, pass a predicate (see `AbstractAuditEventRepository.inclusionPredicate(...)` /
`exclusionPredicate(...)`):

```java
return new FileBasedAuditEventRepository("/var/log/myapp/audit.log", eventMapper,
    AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));
```

## Behavior notes

- **Write failures.** A failure to write is always logged at `ERROR`. By default it additionally throws an
  `AuditEventWriteException`; call `setThrowOnWriteFail(false)` to only log it and continue, so a file system problem
  does not break the operation that triggered the audit event.
- **One writer per file.** The repository backs the file with a Java Util Logging `FileHandler`, which also creates a
  `<name>.lck` lock file next to it. Use a single `FileBasedAuditEventRepository` instance per file within the JVM.
- **Retention.** Rolled files (`<name>-<yyyyMMdd>.<ext>`) accumulate indefinitely — prune them with your normal log
  rotation / retention tooling.

## Spring Boot auto-configuration

Auto-configuration (conditional bean creation, plus properties for the log file path) is not yet provided — wire the
bean manually as shown above. It is planned as a follow-up.
