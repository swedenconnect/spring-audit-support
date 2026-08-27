# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=LibraryVersionTest

# Run a single test method
mvn test -Dtest=LibraryVersionTest#testVersion

# Generate Javadoc
mvn javadoc:aggregate

# Generate code coverage report (target/site/jacoco/index.html)
mvn jacoco:report
```

## Module Structure

This is a three-module Maven project targeting **Java 21** with **Spring Boot 4.x / Spring Framework 7.x**:

| Module | Artifact ID | Purpose |
|--------|-------------|---------|
| `audit-support` | `audit-support` | Core library — audit domain model, services, and Spring Security integration |
| `autoconfigure` | `audit-support-spring-boot-autoconfigure` | Spring Boot auto-configuration |
| `starter` | `audit-support-spring-boot-starter` | Convenience starter (depends on both above) |

Consumers add `audit-support-spring-boot-starter` to get everything.

## Key Dependencies

- **Spring Security Core/Web** — security context integration
- **Spring Boot Actuator** — audit event publishing
- **Spring Data Redis** (optional) — audit event persistence via Redis / Spring Session

Lombok is **not** used — the code is plain Java. Do not introduce Lombok annotations; use hand-written getters/setters/builders and a standard SLF4J `LoggerFactory.getLogger(...)` for logging.

## Architecture Notes

All production code lives under `se.swedenconnect.spring.audit`. The project is in early stages — currently only `LibraryVersion` and package scaffolding exist.

`LibraryVersion` is a package-private utility that exposes a `SERIAL_VERSION_UID` (derived from the version string) used for consistent serialization versioning across domain classes. New serializable classes should reference it.

The `version.properties` file in test resources is Maven-filtered (`${project.version}`) and used by `LibraryVersionTest` to verify the version constant stays in sync with the POM.

## Release

Use the `release` profile to attach source/Javadoc JARs and sign artifacts with GPG before publishing to Maven Central:

```bash
mvn clean deploy -Prelease
```
