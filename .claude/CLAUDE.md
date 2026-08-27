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

# Generate code coverage report (target/site/jacoco/index.html, and jacoco.csv / jacoco.xml)
mvn test jacoco:report
```

Note: several modules depend on `audit-support`, so run `mvn install -DskipTests` before running the tests of
`autoconfigure` after a change in `audit-support`.

## Module Structure

This is a three-module Maven project targeting **Java 21** with **Spring Boot 4.x / Spring Framework 7.x**:

| Module | Artifact ID | Purpose |
|--------|-------------|---------|
| `audit-support` | `audit-support` | Core library — audit domain model, repositories, event transformers |
| `autoconfigure` | `audit-support-spring-boot-autoconfigure` | Spring Boot auto-configuration |
| `starter` | `audit-support-spring-boot-starter` | Convenience starter (depends on both above) |

Consumers add `audit-support-spring-boot-starter` to get everything.

## Key Dependencies

- **Spring Security Core/Web** — used to resolve the principal of an audit event
- **Spring Boot Actuator** — the audit event publication and `AuditEventRepository` infrastructure
- **Jackson 3** (`tools.jackson`) — JSON serialization of audit events. Note: `com.fasterxml.jackson.annotation`
  annotations (`@JsonValue`, `@JsonIgnore`, ...) are still used on the domain classes
- **JSpecify** — nullability annotations, see the global instructions for placement rules
- Optional, for the corresponding repository implementations: **Spring Data Redis**, **Spring JDBC**,
  **Spring Data MongoDB**, **syslog-java-client**

Lombok is **not** used — the code is plain Java. Do not introduce Lombok annotations; use hand-written
getters/setters/builders and a standard SLF4J `LoggerFactory.getLogger(...)` for logging.

## Architecture

All production code lives under `se.swedenconnect.spring.audit`.

### Event flow

```
ApplicationEvent → AuditApplicationListener → EventTransformer → AuditEvent
                            ↓ (context)                              ↓
                   AuditEventContextResolver              AuditApplicationEvent
                                                                     ↓
                                                        AuditEventRepository
```

`AuditApplicationListener` receives every `ApplicationEvent`, asks its `AuditEventContextResolver` for an
`AuditEventContext` (passing the event as input), lets the first supporting `EventTransformer` turn the event into an
`AuditEvent`, and re-publishes the result wrapped in an `AuditApplicationEvent` so that Spring Boot's auditing
infrastructure picks it up. An event that itself implements `EventTransformer` transforms itself — no transformer has
to be registered for it.

### Packages

| Package | Contents |
|---------|----------|
| (root) | `AuditEvent` (the structured event), `AuditEventBuilder`, `AuditType`, `AuditEventContext(Resolver)`, `AuditApplicationListener` |
| `value` | `AuditValue` and its subclasses (`String`, `Integer`, `Boolean`, `Instant`, `List`, `Map`) — the typed content of the event `data` field, plus `AuditValueConstants` factories |
| `transform` | `EventTransformer`, `SingleEventTransformer` (for a single event class), and the built-in system started/shutdown transformers |
| `appevents` | Application events that transform themselves: `SystemAlertEvent` and the `AbstractErrorEvent` base class |
| `repository` | `ExtendedAuditEventRepository` (predicate-based find) and the implementations: in-memory, file, JDBC/MongoDB (via `AuditEventDao`), Redis, syslog, plus `DelegatingAuditEventRepository` |
| `support` | `ApplicationName` |
| `tracing` | `CorrelationID` / `TraceID` value types, the `CorrelationIDHolder` / `TraceIDHolder` static gateways, and the pluggable `IdentifierStorage` (MDC-backed by default) |

### Notable details

- `LibraryVersion` is a package-private utility exposing a `SERIAL_VERSION_UID` (derived from the version string) used
  for consistent serialization versioning. New serializable classes in the `se.swedenconnect.spring.audit` package
  should reference it.
- The `version.properties` file in test resources is Maven-filtered (`${project.version}`) and used by
  `LibraryVersionTest` to verify the version constant stays in sync with the POM.
- `AuditEventBuilder.builder(AuditEventContext)` initializes a builder from a context (application name, correlation
  ID, trace ID, principal). Transformers normally start there.
- Identifiers are never read from MDC directly. `CorrelationIDHolder` / `TraceIDHolder` read and write through the
  installed `IdentifierStorage`, so that a reactive application can replace the thread-bound default. See
  [docs/tracing.md](../docs/tracing.md).
- A repository that cannot serve queries returns `false` from `supportsFind()`, and its `find` methods return an empty
  list.

## Auto-configuration and configuration properties

- `AuditSupportAutoConfiguration` creates the `AuditApplicationListener`, the `AuditEventContextResolver` and the
  `ApplicationName` beans. `AuditRepositoryAutoConfiguration` assembles the `AuditEventRepository`.
- Every bean is `@ConditionalOnMissingBean` — an application can replace any of them.
- **Repository configuration model**: a repository is set up if, and only if, its `audit.repository.<x>` settings are
  configured. Each section has an `enabled` setting defaulting to `true`, so assigning any setting enables the
  repository, `enabled: true` alone enables one whose settings all have defaults, and `enabled: false` turns a
  configured repository off.
- Properties classes implement `InitializingBean`. Required settings are validated in `afterPropertiesSet`, and
  defaults for optional settings are applied there (logged at INFO). A section that is not enabled is not checked.

### Javadoc on `@ConfigurationProperties` fields

The field Javadoc is copied verbatim into `spring-configuration-metadata.json` and shown in IDE YAML completion.
Write it as **plain prose**: no `{@link}`, no `{@code}`, no `{@value}`, no qualified type names. State the default or
that the setting is required, and make the first sentence stand alone. Getters and setters may use normal Javadoc.

Known build issue: the `spring-boot-configuration-processor` is on the classpath but JDK 23+ no longer runs
classpath-discovered annotation processors implicitly, so no metadata file is currently generated. Fixing it requires
`-proc:full` (or an explicit `annotationProcessorPaths` entry) in the compiler plugin configuration.

## Test conventions

- JUnit 5 + AssertJ + Mockito. Spring Boot's `ApplicationContextRunner` for the auto-configuration tests.
- One test class per production class, named `<ClassName>Test`, with a `Test cases for {@link X}.` Javadoc and
  `@author Martin Lindström`.
- Test methods are named `testXxx` and are package-private (`void testXxx()`).
- Tests that touch MDC or the Spring Security context must clear both in an `@AfterEach`.
- Keep the IDE warning-free: serializable test helpers need a `serialVersionUID`, overridden methods keep their
  nullability annotations, and unused imports/return values are removed.

## Release

Use the `release` profile to attach source/Javadoc JARs and sign artifacts with GPG before publishing to Maven Central:

```bash
mvn clean deploy -Prelease
```
