# Delegating Audit Event Repository

`DelegatingAuditEventRepository` is a composite repository that forwards audit events to several underlying
repositories at once — for example, to persist to a database **and** ship to syslog. It implements the library's
[`ExtendedAuditEventRepository`](../audit-support/src/main/java/se/swedenconnect/spring/audit/repository/ExtendedAuditEventRepository.java)
(and therefore Spring Boot's `AuditEventRepository`).

- **`add(event)`** applies this repository's own filter and then forwards accepted events to **all** delegates.
- **`find(principal, after, type)`** and the predicate-based **`find(criteria)`** both try each delegate in order and
  return the **first non-empty** result — results are never merged. They differ in which delegates they can use:
  `find(principal, after, type)` uses **all** delegates, since every `AuditEventRepository` offers that method, while
  `find(criteria)` only uses delegates that are `ExtendedAuditEventRepository` instances supporting find.
- **`supportsFind()`** is `true` if at least one delegate is an `ExtendedAuditEventRepository` whose `supportsFind()`
  is `true` — that is, it reports whether a **predicate-based** query can be served. When it is `false`,
  `find(criteria)` returns an empty list. A plain `AuditEventRepository` delegate does not make it `true`, even though
  such a delegate can still answer `find(principal, after, type)`.

## Delegate order matters

Since a query is answered by the first delegate that returns a result — results are never merged across delegates —
list the delegates in the order you want them consulted: **the most complete store first, the most limited one last**.

In particular, an in-memory repository should always come last. It is a bounded buffer holding only the most recent
events, so a query hitting it first would be answered from that buffer and a durable delegate behind it would never be
consulted. The [auto-configuration](autoconfigure.md) follows this rule and appends the in-memory repository after all
other repositories.

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
