![Logo](images/sweden-connect.png)

# Documenting Audit Events

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

An audit log is only useful if whoever reads it knows what to expect. The point of building events from
[audit values](usage.html#audit-values) rather than an unconstrained map is that each event type has a fixed shape, and
a fixed shape can be written down. This page describes how to write it down.

Every application built on this library should publish a page documenting the audit events it produces. It is part of
the deliverable, in the same way that an API specification is.

[Audit Events](audit-events.html), which documents the events produced by this library, follows the form described
here and can be used as a template.

## Document the common structure once

Start the page by listing the fields every event carries: `type`, `timestamp`, `application_name`, `correlation_id`,
`trace_id`, `principal` and `data`. Say what the principal means in your application, since that is the field whose
meaning varies most between systems. If your application always adds a particular member to `data`, say so here rather
than repeating it in every event.

Do not describe the base structure again for each event. Repetition is where documentation and code drift apart.

## One section per event type

Give each audit event type its own section, in the order events occur during a typical flow rather than
alphabetically. A reader following an audit trail is reading it in time order.

Each section has the same four parts.

**A heading** naming the event in prose, not the type string. "Authentication Request Received" reads better in a table
of contents than `SAML2_REQUEST_RECEIVED`.

**An anchor** before the heading, named after the type, so that other documents and error messages can link straight to
it:

```markdown
<a name="user_login"></a>
## User Login
```

**Type:** the exact type string, as it appears in the log. This is what a reader greps for.

**Description:** when the event is created, in terms of what has happened in the system. Say what has already occurred
and what has not. "The request has been received, but no validation has been performed yet" tells a reader far more
than "a request was received". Note anything conditional: events only produced in certain configurations, events that
may be produced more than once, events that may be missing.

**Audit data:** a table per named object in `data`, headed by the object's name.

## The audit data table

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

## Events built on a shared base

If several of your events extend a common base, document the shared part once and refer to it. Events derived from
[`AbstractErrorEvent`](audit-events.html#error-events) all carry the same `error` object, so each event's own section
lists only what its subclass adds:

```markdown
<a name="signature_failed"></a>
## Signature Failed

**Type:** `signature_failed`

**Description:** Created when a signature operation could not be completed. Carries the common `error` object
described under [Error events](audit-events.html#error-events), plus:

**Audit data:** `signature_info`

| Parameter | Description | Type |
| :--- | :--- | :--- |
| `key_id` | The identifier of the key that was to be used. | String |
```

## Keep it honest

The most common failure of this kind of documentation is that it describes what was intended rather than what is
produced. Write each section from the transformer that builds the event, not from the design note that preceded it, and
check the member names against the code. A single serialized example, taken from a real log rather than written by
hand, is worth including for anything non-trivial.

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
