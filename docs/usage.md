![Logo](images/sweden-connect.png)

# Usage

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

This page describes the concepts of the library and how they fit together. See [Configuration](configuration.html) for
the properties that set it all up, and [Repositories](repositories.html) for where the events end up.

<a name="the-chain"></a>
## From application event to audit entry

An application should not have to know that auditing exists. It publishes an ordinary Spring `ApplicationEvent` saying
what happened, and the audit machinery decides whether that is worth auditing and what the audit entry should look
like.

```
ApplicationEvent  ──▶  AuditApplicationListener  ──▶  EventTransformer  ──▶  AuditEvent  ──▶  AuditEventRepository
```

[`AuditApplicationListener`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditApplicationListener.java)
listens to every application event published in the context. For each one it resolves what to do, in this order:

1. An `AuditApplicationEvent` is ignored. It has already been processed.
2. A `PayloadApplicationEvent` carrying an `AuditEvent` payload is published as an `AuditApplicationEvent`.
3. An event whose source is an `AuditEvent` is published as an `AuditApplicationEvent`.
4. An event that itself implements `EventTransformer` and supports itself is transformed and published.
5. Otherwise, the first registered `EventTransformer` that supports the event is used.

If no transformer matches, the event is ignored. This is the normal case: most application events are not audit events.

Cases 2 and 3 exist because a bare `AuditEvent` published on its own does not reach the actuator's
`AuditEventRepository`. It has to be wrapped in an `AuditApplicationEvent`, and the listener does that wrapping for you.

> Note that the listener uses the **first** transformer that supports an event. Registering two transformers that both
> support the same event type means one of them silently never runs. One transformer per event type.

<a name="event-transformers"></a>
### Event transformers

An
[`EventTransformer`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/transform/EventTransformer.java)
has two methods: `supports(ApplicationEvent)`, which decides whether it handles a given event, and
`transform(ApplicationEvent, AuditEventContext)`, which produces the audit event.

Declare a transformer as a bean and the auto-configuration registers it with the listener.

```java
@Bean
EventTransformer userLoginTransformer() {
  return new UserLoginEventTransformer();
}
```

For the common case of a transformer handling exactly one event class, use
[`SingleEventTransformer`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/transform/SingleEventTransformer.java),
which implements `supports` from a declared event type, so only the transformation itself has to be written.

An event class may also implement `EventTransformer` itself, so that the event carries the knowledge of how it is
audited. Such an event needs no registered bean at all, since the listener finds the transformer on the event. The
library's own
[`SystemAlertEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/appevents/SystemAlertEvent.java)
works this way, and is worth reading as a complete example.

<a name="the-audit-event-context"></a>
### The audit event context

Before a transformer is invoked, the listener asks its
[`AuditEventContextResolver`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditEventContextResolver.java)
for an
[`AuditEventContext`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditEventContext.java).
The context carries the ambient facts that every audit event needs and that no individual transformer should have to
work out for itself: the application name, the correlation ID, the trace ID and the principal.

The default resolver takes the application name from configuration, the correlation ID and trace ID from the
[identifier storage](tracing.html), and the principal from the Spring Security context, falling back to a configured
default principal when no user is authenticated.

The event being audited is passed to the resolver, so a custom resolver may base its result on it. See
[Configuration](configuration.html#customizing-the-audit-event-context) for how to supply your own.

<a name="the-audit-event"></a>
## The audit event

[`AuditEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditEvent.java)
extends Spring Boot's `org.springframework.boot.actuate.audit.AuditEvent`, so it is still a Spring audit event and
still travels through the actuator's infrastructure. What it adds is structure.

Every audit event has the same base fields:

| Field | Content |
| :--- | :--- |
| `type` | The audit event type, the unique name identifying the kind of event. |
| `timestamp` | The instant when the event occurred. The current time is used if none is supplied. |
| `application_name` | The name of the application that produced the event. Omitted if not available. |
| `correlation_id` | The correlation ID tying the event to a flow that may span several requests. Omitted if not available. |
| `trace_id` | The trace ID tying the event to a single request, possibly handled by several services. Omitted if not available. |
| `principal` | The initiator of the audited operation. For events not tied to an end user the system principal, `system`, is normally used. Omitted if not available. |
| `data` | An object holding the event-specific content. Its members are defined by the individual event. |

The application name matters when logs from several applications are shipped to the same log server. The correlation ID
and the trace ID are what let entries be grouped, and they are two different things: a correlation ID may span many
requests, a trace ID lives within one. See [Correlation ID and Trace ID](tracing.html).

Fields that are empty are omitted from the serialized output, so an event carries only what is actually known.

A serialized event has this shape:

```json
{
  "type": "user_login",
  "timestamp": "2026-07-31T09:12:44.001Z",
  "application_name": "my-service",
  "correlation_id": "b1f2c3d4-8a91-4f0e-9c22-7b5d3e1a0f44",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "principal": "198501011234",
  "data": {
    "user_id": "198501011234",
    "display_name": "Alice Andersson"
  }
}
```

Additional fields may be placed at the root level rather than inside `data`, for the rare case where something belongs
alongside the base fields. Use this sparingly. Root level is for facts about the event itself, `data` is for what the
event is about.

<a name="audit-types"></a>
### Audit types

The event type is not a bare string but an
[`AuditType`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditType.java),
a small value type that rejects an empty value.

```java
public static final AuditType USER_LOGIN = AuditType.of("user_login");
```

Declaring the types an application produces as constants in one place gives you the list of what your audit log can
contain, which is the starting point for documenting it.

<a name="audit-values"></a>
## Audit values

In Spring's audit event, the data is a `Map<String, Object>`. Anything can be put in it, under any name, at any call
site. Two entries of the same type may therefore have different members, different names for the same thing, or values
of different types. An audit log built that way cannot be documented reliably, and whoever consumes it, an operator,
an analyst, a log pipeline, cannot depend on the format.

The library builds event data from
[`AuditValue`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/value/AuditValue.java)
objects instead. An `AuditValue` is a named value of a known type. Because the value objects for an event type are
created in one place, every entry of that type comes out with the same members and the same names, and the shape of the
event can be written down and relied upon.

### The value types

| Type | Holds |
| :--- | :--- |
| `StringAuditValue` | A string |
| `IntegerAuditValue` | An integer |
| `BooleanAuditValue` | A boolean |
| `InstantAuditValue` | An `Instant` |
| `ListAuditValue` | A list of serializable values |
| `MapAuditValue` | A nested object of named values |

The simple types are created directly:

```java
final StringAuditValue userId = new StringAuditValue("user_id", "198501011234");
final BooleanAuditValue signed = new BooleanAuditValue("signed", true);
```

`MapAuditValue` and `ListAuditValue` have builders, since they hold more than one thing:

```java
final MapAuditValue authnInfo = MapAuditValue.builder()
    .name("authn_info")
    .value("method", "bankid")
    .value("level", "loa3")
    .build();

final ListAuditValue scopes = ListAuditValue.builder()
    .name("scopes")
    .value("openid", "profile")
    .build();
```

An audit value serializes to its name and value, so the two above become:

```json
"authn_info": {
  "method": "bankid",
  "level": "loa3"
},
"scopes": [ "openid", "profile" ]
```

Names are written in snake case, which is the convention used throughout the serialized event.

### Common values

[`AuditValueConstants`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/value/AuditValueConstants.java)
supplies factory methods for values that recur across applications, so that the same concept gets the same name
everywhere: `userId`, `displayName`, `givenName`, `surname`, `email`, `personalIdentityNumber`, and `error`.

```java
AuditValueConstants.userId("198501011234");         // "user_id": "198501011234"
AuditValueConstants.personalIdentityNumber(pnr);    // "personal_identity_number": ...
```

`error` builds a `MapAuditValue` named `error` with the members `code`, `message`, `exception_class` and `details`:

```java
AuditValueConstants.error("invalid_request", "Missing parameter", IllegalArgumentException.class, null);
```

Use these rather than rolling your own equivalents. Consistent naming across applications is most of the value of a
structured audit log.

<a name="building-an-audit-event"></a>
## Building an audit event

[`AuditEventBuilder`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/AuditEventBuilder.java)
assembles the event. Inside a transformer, build it from the context, which initializes the application name,
correlation ID, trace ID and principal for you:

```java
public class UserLoginEventTransformer implements SingleEventTransformer<UserLoginEvent> {

  public static final AuditType USER_LOGIN = AuditType.of("user_login");

  @Override
  public AuditEvent transformEvent(final UserLoginEvent event, final AuditEventContext context) {
    return AuditEventBuilder.builder(context)
        .type(USER_LOGIN)
        .timestamp(event.getLoginTime())
        .principal(event.getUserId())
        .dataField(AuditValueConstants.userId(event.getUserId()))
        .dataField(AuditValueConstants.displayName(event.getDisplayName()))
        .dataField(MapAuditValue.builder()
            .name("authn_info")
            .value("method", event.getMethod())
            .value("level", event.getLevel())
            .build())
        .build();
  }

  @Override
  public Class<UserLoginEvent> getEventType() {
    return UserLoginEvent.class;
  }
}
```

Anything taken from the context may be overridden, as the principal is above. Without a call to `timestamp(...)` the
current time is used.

`AuditEvent` may also be subclassed for an event that an application produces often, so that its structure is fixed in
a type rather than reassembled at each call site.

<a name="system-alerts"></a>
## System alerts

The library supplies one ready-made event.
[`SystemAlertEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/appevents/SystemAlertEvent.java)
is published by an application to raise an operational condition that operators should know about, typically an error
or an anomaly.

```java
publisher.publishEvent(new SystemAlertEvent("Failed to reach the signature service", exception));
```

It becomes an audit event of type `system_alert` with the system principal, carrying a `data.alert_info` object with
`message`, `exception_class` and `exception_message`. Since the event implements `EventTransformer` itself, nothing
needs to be registered.

For reporting errors, subclass
[`AbstractErrorEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/appevents/AbstractErrorEvent.java).
It takes an error code, and optionally a message, the class of the exception that caused the error, and further
details, and places them in a `data.error` object. The subclass supplies the audit type and may add its own data
fields. Like `SystemAlertEvent`, it transforms itself, so nothing needs to be registered.

The library also supplies two transformers for auditing the application lifecycle,
`ApplicationReadyEventTransformer` and `ContextClosedEventTransformer`, which produce audit events of type
`system_started` and `system_shutdown`. These are registered by the auto-configuration, so no application code is
needed. Declare your own bean of either type to replace the supplied one.

All of the library's own audit events, and their data, are documented under
[Documenting Audit Events](documentation-guide.html#library-events).

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
