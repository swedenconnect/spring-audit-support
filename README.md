![Logo](docs/images/sweden-connect.png)


# Spring Audit Support

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

A framework for audit logging in Spring applications.

-----

## About

Spring Boot ships with audit support in its actuator module, but it is deliberately minimal. An
`org.springframework.boot.actuate.audit.AuditEvent` carries a timestamp, a principal, a type given as a free text
string, and a `Map<String, Object>` of data. Nothing constrains what goes into that map, so the structure of an audit
entry is decided anew at every call site, and two entries of the same type may well look different. There is also only
one repository implementation supplied out of the box, `InMemoryAuditEventRepository`, which keeps a bounded number of
events in memory and loses everything when the application stops. That is enough to demonstrate the actuator's
`auditevents` endpoint, and not enough to run an audited service in production.

This library builds on Spring Boot's audit support rather than replacing it. Everything it produces is still a Spring
`AuditEvent`, and it still travels through the actuator's own infrastructure, so anything that already reads Spring
audit events keeps working.

What it adds:

- **A structured audit event.** `AuditEvent` extends Spring's class with an application name, a correlation ID and a
  trace ID at the root level, so entries from several applications can be told apart and entries belonging to the same
  flow or the same request can be grouped.

- **Audit values.** Event data is built from `AuditValue` objects, each a named value of a known type, rather than
  from an unconstrained map. An event type is then defined once, in one place, and every entry of that type has the
  same shape. That is what makes an audit log documentable, and what lets whoever consumes it rely on the format.

- **A transformation chain.** Applications publish ordinary Spring `ApplicationEvent`s, and registered
  `EventTransformer`s turn them into audit events. Application code does not need to know that auditing exists.

- **Repositories that persist.** Relational databases via JDBC, MongoDB, Redis, syslog, a rolling file, and the
  in-memory repository. A delegating repository writes to several of these at once, applies one event filter for all
  of them, and holds a single policy for what happens when a write fails.

- **Correlation ID and trace ID handling.** Two separate identifiers with separate purposes, kept in a pluggable
  storage so that they work in servlet applications and, in time, in reactive ones.

- **Spring Boot auto-configuration and a starter**, so that the whole chain is set up from properties.

The repository comprises the following modules:

- `audit-support` - The core library: audit events, audit values, the transformation chain and the repositories.

- `autoconfigure` - A Spring Boot autoconfigure module for the audit support library.

- `starter` - A Spring Boot starter for the audit support library.

## Documentation

See [https://docs.swedenconnect.se/spring-audit-support/index.html](https://docs.swedenconnect.se/spring-audit-support/index.html)
for documentation about usage, configuration, repositories and correlation ID handling.

Also, see the [Release Notes](https://docs.swedenconnect.se/spring-audit-support/release-notes.html).

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
