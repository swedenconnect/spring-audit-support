![Logo](images/sweden-connect.png)

# Audit Events

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

This page documents the audit events produced by the library itself. An application will add its own event types on top
of these, and should document them in the same form, see
[Documenting Audit Events](documentation-guide.html).

## Common structure

All events carry the [common audit event structure](usage.html#the-audit-event):

- `type` - The audit event type, see below.
- `timestamp` - The instant when the event occurred.
- `application_name` - The name of the application that produced the event.
- `correlation_id` - The correlation ID tying the event to a flow that may span several requests.
- `trace_id` - The trace ID tying the event to a single request.
- `principal` - The initiator of the audited operation.
- `data` - The event-specific content, described per event below.

Fields that are empty are omitted from the serialized event.

For every event on this page the principal is the system principal, `system`, since none of them is tied to an end
user.

<a name="system_started"></a>
## System Started

**Type:** `system_started`

**Description:** Created when the application has started and is ready to serve requests, in response to Spring Boot's
`ApplicationReadyEvent`. It records that the application came up. The timestamp is the instant Spring reported
readiness, not the instant the audit entry was written.

Note that this event may appear more than once in a single process. An application with a parent and a child context
publishes one per context, and development-time restarts publish it again.

**Audit data:** None beyond the common fields.

<a name="system_shutdown"></a>
## System Shutdown

**Type:** `system_shutdown`

**Description:** Created when the application context is closing, in response to Spring's `ContextClosedEvent`. The
event is published before the context's beans are destroyed, so the audit repositories are still able to write.

An entry is only produced on a **graceful** shutdown. A process that is killed outright never publishes
`ContextClosedEvent`, and so produces no entry. The absence of a `system_shutdown` entry following a `system_started`
entry is therefore itself informative: it means the application did not stop cleanly.

**Audit data:** None beyond the common fields.

<a name="system_alert"></a>
## System Alert

**Type:** `system_alert`

**Description:** Created when an application publishes a
[`SystemAlertEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/appevents/SystemAlertEvent.java)
to raise an operational condition that operators should be made aware of, typically an error or an anomaly. The event
transforms itself, so an application publishes it and nothing needs to be registered.

```java
publisher.publishEvent(new SystemAlertEvent("Failed to reach the signature service", exception));
```

**Audit data:** `alert_info`

| Parameter | Description | Type |
| :--- | :--- | :--- |
| `message` | The alert message. Always present. | String |
| `exception_class` | The fully qualified class name of the exception that triggered the alert. Absent if the alert was raised without an exception. | String |
| `exception_message` | The message of that exception. Absent if there is no exception, or if the exception carries no message. | String |

<a name="error-events"></a>
## Error events

**Type:** Defined by the subclass.

**Description:**
[`AbstractErrorEvent`](https://github.com/swedenconnect/spring-audit-support/blob/main/audit-support/src/main/java/se/swedenconnect/spring/audit/appevents/AbstractErrorEvent.java)
is a base class for application events reporting that something went wrong and should be recorded in the audit log. It
is not an event type in itself. An application subclasses it, supplies the audit type, and may add its own data fields.

Like `SystemAlertEvent`, a subclass transforms itself, so nothing needs to be registered.

Every event derived from `AbstractErrorEvent` carries the `error` object below. Whatever the subclass adds is
documented by the application, alongside its own event types.

**Audit data:** `error`

| Parameter | Description | Type |
| :--- | :--- | :--- |
| `code` | The error code. Always present. | String |
| `message` | The error message. Absent if none was supplied. | String |
| `exception_class` | The fully qualified class name of the exception that caused the error. Absent if none was supplied. | String |
| `details` | Further details about the error. Absent if none were supplied. | String |

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
