![Logo](images/sweden-connect.png)

# Documenting Audit Events

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

An audit log is only useful if whoever reads it knows what to expect. The point of building events from
[audit values](usage.html#audit-values) rather than an unconstrained map is that each event type has a fixed shape, and
a fixed shape can be written down. This page describes how to write it down, and then documents the audit events that
this library itself produces, as a worked example.

Every application built on this library should publish a page like the one in the second half of this document. It is
part of the deliverable, in the same way that an API specification is.

<a name="guidelines"></a>
## Guidelines

### Document the common structure once

Start the page by listing the fields every event carries: `type`, `timestamp`, `application_name`, `correlation_id`,
`trace_id`, `principal` and `data`. Say what the principal means in your application, since that is the field whose
meaning varies most between systems. If your application always adds a particular member to `data`, say so here rather
than repeating it in every event.

Do not describe the base structure again for each event. Repetition is where documentation and code drift apart.

### One section per event type

Give each audit event type its own section, in the order events occur during a typical flow rather than
alphabetically. A reader following an audit trail is reading it in time order.

Each section has the same four parts:

**A heading** naming the event in prose, not the type string. "Authentication Request Received" reads better in a table
of contents than `SAML2_REQUEST_RECEIVED`.

**An anchor** before the heading, named after the type, so that other documents and error messages can link straight to
it:

```markdown
<a name="user_login"></a>
### User Login
```

**Type:** the exact type string, as it appears in the log. This is what a reader greps for.

**Description:** when the event is created, in terms of what has happened in the system. Say what has already occurred
and what has not. "The request has been received, but no validation has been performed yet" tells a reader far more
than "a request was received". Note anything conditional: events that are only produced in certain configurations,
events that may be produced more than once, events that may be missing.

**Audit data:** a table per named object in `data`, headed by the object's name.

### The audit data table

Use the same three columns everywhere:

| Parameter | Description | Type |
| :--- | :--- | :--- |
| `member_name` | What it contains, and when it is absent. | String |

Points worth keeping to:

- Name the member exactly as it is serialized, in backticks. Snake case, since that is what the serialization produces.
- Use a dotted path for nested members, so `status.code` rather than a nested table.
- State the type in the terms a reader of JSON would use: String, Boolean, Integer, Instant, a list of strings, or a
  named object described by its own table.
- Say when a member is absent. A member that is omitted when empty is different from one that is always present, and a
  reader parsing the log needs to know which.
- If a member has a fixed or enumerated value, list the values.

If an event carries no data beyond the common fields, say so in one sentence instead of writing an empty table.

### Keep it honest

The most common failure of this kind of documentation is that it describes what was intended rather than what is
produced. Write the section from the transformer that builds the event, not from the design note that preceded it, and
check the member names against the code. A single serialized example, taken from a real log rather than written by
hand, is worth including for anything non-trivial.

-----

<a name="library-events"></a>
## Audit events produced by this library

The events below are produced by the library itself. An application will add its own on top of these.

All of them carry the [common audit event structure](usage.html#the-audit-event): `type`, `timestamp`,
`application_name`, `correlation_id`, `trace_id`, `principal` and `data`. Fields that are empty are omitted.

For all library events the principal is the system principal, `system`, since none of them is tied to an end user.

<a name="system_started"></a>
### System Started

**Type:** `system_started`

**Description:** Created when the application has started and is ready to serve requests, in response to Spring Boot's
`ApplicationReadyEvent`. It records that the application came up, and its timestamp is the instant Spring reported
readiness rather than the instant the audit entry was written.

Note that this event may appear more than once in a single process. An application with a parent and a child context
publishes one per context, and development-time restarts publish it again.

**Audit data:** none beyond the common fields.

<a name="system_shutdown"></a>
### System Shutdown

**Type:** `system_shutdown`

**Description:** Created when the application context is closing, in response to Spring's `ContextClosedEvent`. The
event is published before the context's beans are destroyed, so the audit repositories are still able to write.

An entry is only produced on a **graceful** shutdown. A process that is killed outright never publishes
`ContextClosedEvent`, and so produces no entry. The absence of a `system_shutdown` entry following a `system_started`
entry is therefore itself informative: it means the application did not stop cleanly.

**Audit data:** none beyond the common fields.

<a name="system_alert"></a>
### System Alert

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
### Error events

**Type:** defined by the subclass.

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

An application defining an error event documents it in the same form, listing the members its subclass contributes in
addition to those above:

```markdown
<a name="signature_failed"></a>
### Signature Failed

**Type:** `signature_failed`

**Description:** Created when a signature operation could not be completed. Carries the common `error` object
described under [Error events](#error-events), plus:

**Audit data:** `signature_info`

| Parameter | Description | Type |
| :--- | :--- | :--- |
| `key_id` | The identifier of the key that was to be used. | String |
```

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
