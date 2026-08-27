![Logo](images/sweden-connect.png)

# Correlation ID and Trace ID

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

Audit events may carry two identifiers that let you group related events together: a **correlation ID** and a **trace
ID**. They answer different questions, and the library treats them as separate concepts.

Both live in the
[`se.swedenconnect.spring.audit.tracing`](https://github.com/swedenconnect/spring-audit-support/tree/main/audit-support/src/main/java/se/swedenconnect/spring/audit/tracing)
package.

<a name="the-difference"></a>
## The difference

| | Correlation ID | Trace ID |
| :--- | :--- | :--- |
| Lifetime | May span **many** requests | Lives within **one** request |
| Reach | Whatever the application decides | May span **several services** handling that request |
| Assigned by | The **application**, from its own logic | The edge of the request, or a tracing framework |
| Application writes it | Yes | No, read only |

A **correlation ID** ties together everything belonging to the same logical flow, and that flow is usually longer than a
single HTTP request. A SAML authentication involves an authentication request, a user interacting with a login page
over several requests, and a response: one correlation ID, many requests. What counts as "the same flow" is a question
only the application can answer, which is why the application assigns the value. Typical sources are a SAML request ID,
an operation ID supplied from a UI, or a case number.

A **trace ID** identifies one request as it travels through a system. If service A calls service B, both should log the
same trace ID for that request, so the two sets of log lines can be lined up. The application never chooses the value.
It is either created at the edge of the request or supplied by a tracing framework.

The two are not alternatives. An application may well have both: a correlation ID grouping a user's whole login flow,
and a different trace ID for each individual request within it.

<a name="the-value-types"></a>
## The value types

`CorrelationID` and `TraceID` are immutable value types wrapping a non-empty string. They are the declared types on
[`AuditEvent`](usage.html#the-audit-event), and they serialize to plain JSON strings.

```java
final CorrelationID id = CorrelationID.of("saml-req-8f21a0");
final CorrelationID generated = CorrelationID.generate();
```

They are values only. Neither has a setter, since an audit event must not change after it has been created. Assigning
"the current identifier" is the job of the holders described next.

<a name="the-holders"></a>
## The holders

`CorrelationIDHolder` and `TraceIDHolder` are the static entry points to the identifier that is current right now. They
follow the same pattern as Spring's `SecurityContextHolder`: a static gateway, separate from the value it holds.

They are static on purpose. A service class deep in a call chain needs to be able to assign a correlation ID the moment
it learns one, after parsing a SAML request, say, without having a bean injected for the purpose.

```java
// Somewhere in application code, once the SAML request has been parsed:
CorrelationIDHolder.set(CorrelationID.of(authnRequest.getID()));
```

`CorrelationIDHolder` reads, writes and clears. `TraceIDHolder` only reads, since the application never assigns a trace
ID. The component that does assign one uses `TraceIDWriter`, which is not part of the application facing surface.

Anything that sets a correlation ID is responsible for clearing it again when the flow ends, so the value is not left
behind for unrelated work. `clear()` never throws, so it is safe in a `finally` block.

Assigning a correlation ID never fails either. If the installed storage has nowhere to keep the value, the value is
dropped and the storage logs it. The audit events of that flow are then written without a correlation ID, which is
preferable to failing the operation being audited for the sake of a diagnostic identifier.

The audit machinery reads these holders through
[`DefaultAuditEventContextResolver`](usage.html#the-audit-event-context), so an event published while a correlation ID
is set gets that ID attached automatically. You do not need to put it on each event yourself.

<a name="storage"></a>
## How the values are stored

Where the current identifier is actually kept is pluggable, because the right answer depends on the kind of application.
`IdentifierStorage` is the contract, and `IdentifierStorageHolder` decides which implementation is in use.

Out of the box the library uses `MdcIdentifierStorage`, which keeps identifiers in SLF4J's MDC. That has two benefits:
it needs no dependencies beyond the logging you already have, and the identifiers appear in your ordinary log output.
Add the keys to your log pattern and every log line written during the flow carries them:

```
%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %X{correlationID} %X{auditTraceID} %logger{36} - %msg%n
```

The keys are `correlationID` and `auditTraceID`, available as constants on the respective holder. The trace ID key is
deliberately not `traceId`, which is what Micrometer Tracing uses for the trace ID it manages. Keeping the keys apart
means this library and a tracing framework can coexist in one application without overwriting each other's values, and
that a log pattern referring to either one is unambiguous. It is not `traceID` either, since a key differing from
Micrometer's only in the case of one letter is a trap for whoever writes that pattern.

MDC is thread local in every SLF4J backend. For a servlet application, where one thread handles a request from start to
finish, that is exactly right and nothing more is needed.

For a **reactive** application it is not enough. A reactive chain moves between threads, so a thread local value may be
missing further down the chain, or, worse, may be a leftover from an unrelated earlier request that used the same
pooled thread. That produces audit records carrying the *wrong* correlation ID, which is harder to notice than a
missing one.

This is why storage is pluggable rather than fixed. An application that needs another storage installs it during
startup, before any audit event is created, using `IdentifierStorageHolder.setStorage(...)`. Applications do not
interact with the storage directly, since the holders are the API.

> **Coming feature.** A reactive capable storage implementation, installed automatically by the starter, is not yet
> available. Until it is, correlation IDs are only reliable in servlet applications. The reactive implementation will
> not require Micrometer or any tracing framework.

### Tests

Installing a storage replaces the one currently installed rather than being refused, because this is process wide state
and a test that could not restore it would leak into the tests that follow. `IdentifierStorageHolder.resetStorage()`
restores the default, and is what a test should call when it is done.

<a name="setting-a-correlation-id"></a>
## Setting a correlation ID

The library does not assign correlation IDs for you, and deliberately so. A value generated per request would live for
exactly one request, and that is a trace ID, not a correlation ID. Only the application knows what constitutes a flow.

So set one when your application knows what the flow is, and clear it when the flow ends.

```java
CorrelationIDHolder.set(CorrelationID.of(operationId));
try {
  // ... work that should be audited under this correlation ID
}
finally {
  CorrelationIDHolder.clear();
}
```

If you have no meaningful identifier of your own, `CorrelationID.generate()` produces a random one.

<a name="coming-features"></a>
## Coming features

None of the following is implemented yet. It is described here so that the current design can be understood in context.

**HTTP propagation.** Filters and client interceptors that read a correlation ID or trace ID from an incoming HTTP
header and attach it to outgoing REST calls, so the identifier survives a hop between services. Servlet and reactive
both. Inbound and outbound will be configured separately, so that a service at the edge of a system can propagate
outbound without trusting whatever a client sends inbound.

**Inbound validation.** A pluggable check on values arriving in HTTP headers. Values from outside end up in log lines
and in stored audit records, so an unchecked header is a log injection route. There will be a default implementation
with sensible limits, and applications will be able to supply a stricter one.

**Optional Micrometer Tracing integration.** When Micrometer Tracing is in use, trace IDs are already created and
propagated by that framework over the standard `traceparent` header. The library will read the trace ID from Micrometer
rather than managing its own, and will not send a trace header of its own.

This integration will be **optional in both directions**. Applications using Micrometer get trace IDs from it, and
applications not using Micrometer get a working trace ID from the library's own filters. Neither group is required to
adopt the other's machinery, and no tracing framework is needed to use this library.

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
