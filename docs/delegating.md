# Delegating Audit Event Repository

`DelegatingAuditEventRepository` is a composite repository that forwards audit events to several underlying
repositories at once — for example, to persist to a database **and** ship to syslog. It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`).

- **`add(event)`** applies this repository's own filter and then forwards accepted events to **all** delegates.
- **`find(principal, after, type)`** (and the predicate-based `find`) try each delegate in order and return the
  **first non-empty** result.
- **`supportsFind()`** is `true` if at least one delegate can serve queries.

## Filtering

Configure the event **filter on the delegating repository**, not on the individual delegates. Filtering there is
applied once, consistently, for every delegate — whereas per-delegate filters are easy to get out of sync. See
`AbstractAuditEventRepository.inclusionPredicate(...)` / `exclusionPredicate(...)` for building filters.

## Write-failure handling

Every delegate is attempted even if an earlier one fails, and each failure is logged. Whether the overall `add(...)`
then throws an `AuditEventWriteException` is resolved **per delegate**:

| Delegate's `throwOnWriteFail` | Result on that delegate's failure |
|---|---|
| explicitly `true` | throw — the delegate's choice wins |
| explicitly `false` | log only — the delegate's choice wins |
| unset | inherit the delegating repository's `setThrowOnWriteFail(...)` value (which itself defaults to throwing) |

If, after all delegates have been attempted, any failed delegate's resolved value was "throw", the delegating
repository throws an `AuditEventWriteException` (the first failure; the rest are attached as suppressed exceptions).

In other words: set the policy once on the delegating repository, and override it on a specific delegate only when that
delegate genuinely needs different behavior (for example, "the local file sink must never break the request, but a
failure to write to the database should").

## Wiring it up

```java
@Bean
AuditEventRepository auditEventRepository(final DatabaseAuditEventRepository database,
    final SyslogAuditEventRepository syslog) {

  final DelegatingAuditEventRepository repository =
      new DelegatingAuditEventRepository(List.of(database, syslog),
          AbstractAuditEventRepository.exclusionPredicate(List.of("noisy_event_type")));

  // Best-effort by default: a failing sink should not break the audited operation.
  repository.setThrowOnWriteFail(false);
  return repository;
}
```

Because the filter is on the delegating repository, the `jdbc` and `syslog` delegates above should be created **without**
their own filters.
