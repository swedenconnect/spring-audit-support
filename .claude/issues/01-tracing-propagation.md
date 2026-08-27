# Propagate correlation ID and trace ID over HTTP, with and without Micrometer

## Summary

Add HTTP propagation for the two identifiers the library already models, so they survive a hop between services. This
covers reading them from incoming requests, attaching them to outgoing REST calls, and integrating with Micrometer
Tracing when the application uses it.

**The library must be fully usable without Micrometer, or any other tracing framework.** That is a hard requirement,
not a nice-to-have. Applications that do not run distributed tracing must still get working correlation IDs and trace
IDs from this library alone. Applications that do run Micrometer must not end up with two competing implementations.

## Background: the two identifiers

The library distinguishes two concepts, and the distinction drives the entire design. See
[Correlation ID and Trace ID](https://docs.swedenconnect.se/spring-audit-support/tracing.html) for the user-facing
description, and [Usage](https://docs.swedenconnect.se/spring-audit-support/usage.html) for how audit events are put
together.

**Correlation ID.** An identifier that may span **many requests**, assigned by the **application** from its own logic.
A SAML authentication flow involves an authentication request, a user interacting with a login page over several
requests, and a response. That is one correlation ID across many requests. Only the application knows what constitutes
a flow, so the library never generates one automatically. Typical sources are a SAML request ID, an operation ID from a
UI, or a case number.

**Trace ID.** An identifier that lives within **one request** but may span **several services** handling it. The
application never assigns it. It is created at the edge of a request, or supplied by a tracing framework.

This asymmetry has consequences throughout: the correlation ID is readable and writable by application code, the trace
ID is read only.

## What already exists

The `se.swedenconnect.spring.audit.tracing` package provides the foundation this work builds on:

- `CorrelationID` and `TraceID`, immutable value types, declared on `AuditEvent`
- `CorrelationIDHolder` and `TraceIDHolder`, the static, application facing entry points to the current identifier.
  `CorrelationIDHolder` reads, writes and clears; `TraceIDHolder` only reads.
- `TraceIDWriter`, the write path for the component that assigns a trace ID, deliberately kept off the application
  facing surface.
- `IdentifierStorage`, the pluggable storage contract, with `MdcIdentifierStorage` as the default implementation and
  `IdentifierStorageHolder` deciding which one is installed.

Storage is pluggable specifically so that a reactive implementation can be installed without `audit-support` gaining
any knowledge of Reactor. This issue is where that reactive implementation gets built.

See [Correlation ID and Trace ID](https://docs.swedenconnect.se/spring-audit-support/tracing.html) for the full
description of what exists today, including the "Coming features" section that this issue is meant to deliver.

## Scope

### A new module: `audit-support-web`

The web dependencies deliberately do not live in `audit-support`, which is kept free of servlet, WebFlux and Reactor.
This work needs a fourth module holding the filters, interceptors, storage implementations and their auto-configuration.

Servlet and reactive support both belong here, with their respective dependencies optional and the auto-configuration
conditional, so that a servlet application never pulls in WebFlux and vice versa. Whether that is genuinely workable in
one module or whether servlet and reactive should be separate modules is a decision point, listed below.

### Inbound

A servlet `Filter` and a reactive `WebFilter` that read the configured headers from an incoming request, validate the
values, and install them via the holders. They must clear the values when the request completes, including on the error
path. A value left behind on a pooled thread will be picked up by an unrelated later request.

The filters must run as early as possible in the chain, so that everything downstream, including anything that logs,
sees the identifiers.

Optionally, the identifiers are echoed back in response headers. This is useful for support cases and for clients that
want to record the identifier they were served under.

### Outbound

An interceptor for the servlet side and an exchange filter for the reactive side, plus the customizers needed to attach
them automatically to `RestClient`, `RestTemplate` and `WebClient` instances built through the Spring Boot builders.

Outbound propagation must default to off. An application should opt in to sending internal identifiers to other
services.

### Reactive storage

A storage strategy implementation that survives thread hops, installed by the auto-configuration when the application
is reactive.

MDC is thread local in every SLF4J backend, so a value written at the edge of a reactive request is not visible after
the chain moves to another thread, and worse, a leftover value from an unrelated request may be visible instead. That
produces audit records carrying the **wrong** correlation ID, which is considerably harder to notice than a missing one.

The proposed approach: the filter opens a request scoped holder at the edge, a stable reference to it is placed in the
Reactor `Context` once and never rewritten, and `io.micrometer:context-propagation` restores it into a thread local at
each operator boundary via a `ThreadLocalAccessor` we register. Application code continues to call the holders and
never touches Reactor.

Two things to note about that library. Despite the group ID, `io.micrometer:context-propagation` is a small standalone
library that does not pull in Micrometer core or Micrometer Tracing, so it does not compromise the no-Micrometer
requirement. And Reactor's automatic context propagation must be enabled for it to work, which is a global hook
affecting the whole application, not just audit.

An alternative was considered and rejected: writing the correlation ID directly into the Reactor `Context` mid-chain.
The `Context` is immutable and propagates from subscription downwards, so a call like "set the correlation ID now"
cannot alter the surrounding context. Requiring application code to use `contextWrite` would break the uniform API
between servlet and reactive and force users to understand Reactor internals.

### Trace ID source abstraction

Where the trace ID comes from depends on the application:

- **The library's own filter.** Extracted from the configured header, or created at the edge if absent.
- **Micrometer Tracing.** The framework already creates and propagates trace IDs over the standard `traceparent`
  header. We read the value from the `Tracer` rather than managing our own, and we do not send a trace header of our
  own.
- **Nothing.** No trace ID, which is the current behaviour.

An abstraction over these, selected at configuration time, keeps the rest of the library indifferent to which is in
play.

Detection for the Micrometer case should require both `micrometer-tracing` on the classpath **and** a `Tracer` bean
that is not `Tracer.NOOP`. An application can perfectly well use Micrometer for metrics with no tracing configured, and
in that case the `Tracer` is the no-op instance. Reading from `Tracer.currentSpan()` is more reliable than reading
Micrometer's MDC key, since that key is only populated when the application has enabled the correlation fields bridge.

There should also be a property to force a specific source, because auto-detection will get it wrong for someone.

**We never construct a `traceparent` header ourselves.** A conforming value needs a new parent ID per hop and the
sampled flag, and the sampled flag is not recoverable from MDC. Half implementing the W3C Trace Context specification
gives the worst of both worlds. Either Micrometer handles it properly, or we use our own simple header.

### Audit context resolution

When a trace ID source is active, a second `AuditEventContextResolver` supplies the trace ID to audit events. It should
wrap the existing `DefaultAuditEventContextResolver` rather than replace or duplicate it, so that principal and
application name resolution stays in one place.

Today `DefaultAuditEventContextResolver` returns `null` for the trace ID unconditionally, so nothing currently
populates the field.

### Inbound validation

An interface with a default implementation, applied to values arriving in HTTP headers.

This is the trust mechanism. An application at the edge of a system either disables inbound propagation or supplies a
stricter implementation as a bean. Whether inbound values should be trusted is a deployment decision, so the
documentation must state the risk plainly rather than assuming a safe default covers it.

The non-negotiable part is rejecting CR, LF and control characters. These values end up in log lines and in stored
audit records, so an unchecked header is a log injection route. A maximum length is also needed, since the value is
persisted.

A rejected value must never fail the request. Drop the value, log at an appropriate level, and continue.

## Proposed configuration

All settings nested under the existing `audit` prefix. See
[Configuration](https://docs.swedenconnect.se/spring-audit-support/configuration.html) for the conventions the existing
properties follow.

```yaml
audit:
  tracing:
    correlation-id:
      header-name: X-Correlation-ID
      inbound:
        enabled: false
        echo-in-response: false
      outbound:
        enabled: false

    trace-id:
      source: auto            # auto | library | micrometer | none
      header-name: X-Trace-Id # only meaningful when source resolves to "library"
      micrometer-mdc-key: traceId  # only meaningful when source resolves to "micrometer"
      inbound:
        enabled: false
        echo-in-response: false
      outbound:
        enabled: false

    filter-order: <as early as possible>

    validation:
      max-length: 128
      # character policy, to be defined by the default implementation
```

Notes on the shape:

**Inbound and outbound are separate**, rather than a single flag, because they are different mechanisms serving
different postures. Inbound is a filter, outbound is a client interceptor. A service at the edge of a system may want
to propagate outbound without trusting anything a client sends inbound, and an internal service may want the reverse.

**There is no `source` under `correlation-id`**, since a correlation ID always comes from the application or from an
inbound header. There is no framework alternative.

**`header-name` under `trace-id` only applies when the library is managing trace IDs.** Under Micrometer, propagation
happens over `traceparent` and this setting is meaningless. Setting it while Micrometer is driving should produce a
startup warning rather than being silently ignored.

**Header name defaults.** `X-Correlation-ID` and `X-Trace-Id` are familiar, but RFC 6648 discourages the `X-` prefix
for new headers. `Correlation-ID` and `Trace-Id` are the more modern choice. Worth a decision rather than defaulting to
habit.

**The storage keys are deliberately not configurable.** The correlation ID is stored under `correlationID` and the
library's own trace ID under `auditTraceID`, both fixed constants on the respective holder. The trace ID key avoids
`traceId`, which is what Micrometer Tracing uses, so that the two can coexist in one application without overwriting
each other, and it avoids `traceID` too, since a key differing from Micrometer's by the case of one letter is a trap
for whoever writes the log pattern. Making these configurable would force the holders' static API to become instance
based, which ripples through everything.

The configurable `micrometer-mdc-key` above is a different thing: it is the key the library *reads* when Micrometer is
the trace ID source, and it has to match whatever the application's tracing setup writes.

## Pitfalls

These are the things most likely to cause a subtle, hard to diagnose problem. They should be handled in the
implementation and called out in the documentation.

**Reactive MDC gives wrong values, not missing values.** Covered above. This is the single biggest risk in the work.
A test on a simple handler chain will often pass, because with Netty the whole pipeline frequently runs on one event
loop thread. It breaks later, under a scheduler hop that was not there during development.

**Automatic context propagation captures at subscription time.** Anything written to MDC directly inside a reactive
chain will not propagate. The rule for reactive applications is that identifiers are set through the holders, and MDC
is read only. Writing to MDC mid-chain will appear to work sometimes.

**Enabling automatic context propagation is a global change.** It affects the whole application, not just audit. A
library turning it on silently is a large lever to pull without asking, so it should be a property, and the
documentation should say what it does.

**Outbound propagation only reaches clients built through the Spring Boot builders.** An application doing
`new RestTemplate()` gets nothing, with no warning. This looks exactly like a library bug when someone hits it, so it
needs to be prominent in the documentation.

**Filter ordering relative to Spring Security.** If the tracing filter runs after the security filter chain,
authentication failures are audited without a correlation ID, which is precisely when you want one. Running before
security means the identifier is installed before the request is authenticated. Pick one deliberately and document it.

**Micrometer present but not tracing.** An application using Micrometer for metrics only has a `Tracer.NOOP`. Treating
the presence of the classpath entry as "tracing is active" will silently disable the library's own trace IDs and
provide nothing in their place.

**Both the library filter and Micrometer writing trace IDs.** If a trace source of `library` is forced while Micrometer
Tracing is active, both would write. Failing at startup is preferable to silently double writing, because the resulting
behaviour is difficult to debug.

**Stale values on pooled threads.** Clearing is mandatory on every exit path, including error paths. This applies to
the servlet filter as much as the reactive one.

**Duplicate or repeated headers.** An incoming request may carry the same header more than once, whether through a
proxy or deliberately. The behaviour needs defining: take the first, take the last, or reject.

**Correlation IDs leaving the perimeter.** With outbound propagation on, the identifier goes to every host the
application calls, including third parties. This is an internal identifier crossing a trust boundary. Shipping it as
all hosts and documenting the concern is probably right for a first version, but an allow-list may be wanted later.

## Decision points

Things that need an answer before or during implementation. Several were discussed and deliberately left open.

1. **Does the library generate a trace ID when the header is absent?** The equivalent answer for correlation IDs is no,
   because a per request value would not be a correlation ID at all. For trace IDs the argument runs the other way: a
   trace ID that only sometimes exists cannot group a request's log lines, and an edge service has to be the one that
   starts it. The two answers are opposites, so the trace ID case should be settled explicitly.

2. **One validator bean, or one per identifier?** Separate beans are more flexible, a single bean is simpler and
   probably sufficient.

3. **Should the reactive auto-configuration refuse to start** if context propagation is unavailable or not enabled?
   Without it, a reactive application silently produces wrong correlation IDs. Failing fast is safer, but it is a
   strong stance for a library to take.

4. **One `audit-support-web` module, or separate servlet and reactive modules?** Reactive has a hard prerequisite that
   servlet does not, which is an argument for splitting, so that a servlet user never encounters the reactive
   dependency or its documentation.

5. **Header naming**, per the RFC 6648 point above.

6. **What happens when `trace-id.header-name` is set while Micrometer is driving?** Warn at startup, or fail.

7. **Filter order relative to Spring Security**, per the pitfall above.

8. **Behaviour when a correlation ID is set outside any request scope**, or in an application with no filter installed.
   Under the MDC implementation this always works, so the question only becomes real under the reactive implementation.

## Out of scope

- Constructing or parsing `traceparent` ourselves
- Per destination outbound allow-lists
- Path exclusions for the inbound filters
- Any change to the fixed storage keys, `correlationID` and `auditTraceID`

## Acceptance criteria

- A servlet application with no tracing framework gets working correlation ID and trace ID propagation, inbound and
  outbound, using only this library.
- A reactive application gets the same, with correct values under thread hops. This needs a test that forces a
  scheduler boundary, not just a simple handler chain.
- An application using Micrometer Tracing gets trace IDs from Micrometer, and the library does not send a competing
  trace header.
- An application using Micrometer for metrics only, with a no-op `Tracer`, still gets trace IDs from the library.
- `audit-support` gains no dependency on servlet, WebFlux, Reactor or Micrometer.
- A servlet application does not pull in WebFlux, and a reactive application does not pull in the servlet API.
- Inbound values containing CR, LF or control characters never reach a log line or a stored audit record, and a
  rejected value never fails the request.
- Identifiers are cleared on every request exit path, including error paths, verified by a test that reuses a thread.
- The documentation is updated. `docs/tracing.md` loses the parts of its
  [Coming features](https://docs.swedenconnect.se/spring-audit-support/tracing.html#coming-features) section that this
  work delivers, and the new properties are added to the table in `docs/configuration.md`. If any new audit event type
  is introduced, it is documented in `docs/audit-events.md` following
  [Documenting Audit Events](https://docs.swedenconnect.se/spring-audit-support/documentation-guide.html).
