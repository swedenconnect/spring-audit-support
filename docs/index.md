![Logo](images/sweden-connect.png)

# Spring Audit Support

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

-----

The [spring-audit-support](https://github.com/swedenconnect/spring-audit-support) repository comprises a library for
audit logging in Spring Boot applications.

Spring Boot's actuator module already has audit support, but it is basic. An audit event is a timestamp, a principal, a
type given as a free text string and an unconstrained map of data, and the only repository supplied out of the box
keeps events in memory until the application stops.

This library builds on that support rather than replacing it. It adds structure to the audit events, so that an event
type has a defined and documentable shape, a chain that turns ordinary Spring application events into audit events, and
a set of repositories that actually persist what is produced. Everything it publishes remains a Spring `AuditEvent`
travelling through the actuator's own infrastructure.

- [Usage](usage.html) - The concepts: application events, audit events, audit types and audit values, and how they fit
  together.

- [Configuration](configuration.html) - Auto-configuration and the complete set of properties.

- [Repositories](repositories.html) - The available repositories: in-memory, file, JDBC, MongoDB, Redis, syslog and the
  delegating repository.

- [Correlation ID and Trace ID](tracing.html) - The two identifiers used to group audit events, and how they are stored.

- [Audit Events](audit-events.html) - The audit events produced by the library, and their data.

- [Documenting Audit Events](documentation-guide.html) - How to document the audit events an application produces.

- [Release Notes](release-notes.html)

-----

Copyright &copy; 2026, [Myndigheten för digital förvaltning - Swedish Agency for Digital Government (DIGG)](http://www.digg.se). Licensed under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
