/*
 * Copyright 2026 Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.swedenconnect.spring.audit;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.tracing.TraceID;
import se.swedenconnect.spring.audit.value.AuditValue;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An extension of Spring's {@link org.springframework.boot.actuate.audit.AuditEvent} that adds more structure to the
 * audit event.
 * <p>
 * Compared to Spring's {@link org.springframework.boot.actuate.audit.AuditEvent}, {@code correlation_id},
 * {@code trace_id} and {@code application_name} fields are added on root level. The application name is useful to
 * include if logs from several different applications are sent to a log server. The correlation ID and traceID are
 * useful to include so that several entries may be grouped together. A correlation ID groups events from a specific
 * operation that may span over several requests, where a trace ID groups events from the same request (but possibly
 * distributed over several services).
 * </p>
 * <p>
 * It is also possible to add additional fields by supplying them in the {@code rootFields} parameter of the
 * constructor.
 * </p>
 * <p>
 * Every audit event produced by this library shares the base structure documented below. The event-specific content is
 * carried in the {@code data} field, whose members are defined by the individual event; see the documentation of the
 * respective event.
 * </p>
 * <table border="1">
 *   <caption>Base audit event fields</caption>
 *   <thead>
 *     <tr><th>Field</th><th>Content</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code type}</td>
 *       <td>The audit event type, i.e., the unique name identifying the kind of event.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code timestamp}</td>
 *       <td>The instant when the event occurred. The current time is used if none is supplied.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code application_name}</td>
 *       <td>The name of the application that produced the event. Optional &ndash; omitted if not available.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code correlation_id}</td>
 *       <td>The correlation ID that ties the event to a specific flow or session. Omitted if not available.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code trace_id}</td>
 *       <td>The correlation ID that ties the event to a specific request. Omitted if not available.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code principal}</td>
 *       <td>The principal (initiator) of the audited operation. For events not tied to an end user, the system
 *         principal ({@value #SYSTEM_PRINCIPAL}) is typically used. Omitted if not available.</td>
 *     </tr>
 *     <tr>
 *       <td>{@code data}</td>
 *       <td>An object holding the event-specific audit data. Its members are defined by the individual event.</td>
 *     </tr>
 *   </tbody>
 * </table>
 * <p>
 * Any additional {@code rootFields} supplied to the constructor are serialized as further members at the root level.
 * Empty fields are omitted from the serialized output.
 * </p>
 * <p>
 * A serialized audit event thus has the following shape:
 * </p>
 * <pre>
 * {
 *   "type": "the_event_type",
 *   "timestamp": "2026-07-31T09:12:44.001Z",
 *   "application_name": "my-service",
 *   "correlation_id": "b1f2c3d4-...",
 *   "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
 *   "principal": "system",
 *   "data": {
 *     ... event-specific fields ...
 *   }
 * }
 * </pre>
 * <p>
 * Note: This class may be subclassed for specific events and easy use, for example, setting up an event for successful
 * logins.
 * </p>
 *
 * @author Martin Lindström
 * @see AuditEventBuilder
 */
@JsonPropertyOrder({ "type", "timestamp", "application_name", "correlation_id", "trace_id", "principal" })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuditEvent extends org.springframework.boot.actuate.audit.AuditEvent {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** Symbolic constant representing the system principal. */
  public static final String SYSTEM_PRINCIPAL = "system";

  /** The application name. */
  @JsonProperty(value = "application_name", required = false)
  private final ApplicationName applicationName;

  /** The correlation ID. */
  @JsonProperty(value = "correlation_id", required = false)
  private final CorrelationID correlationId;

  /** W3C Trace Context trace-id. */
  @JsonProperty(value = "trace_id", required = false)
  private final TraceID traceId;

  /** Additional root fields. */
  private final Map<String, Serializable> rootFields;

  /**
   * Constructor setting upp a structured audit event object.
   *
   * @param type the audit event type, i.e., the unique name for this event
   * @param timestamp the instant when the event occurred, if {@code null}, the current time is used
   * @param applicationName name of the application that performs the audit logging (optional)
   * @param correlationId the correlation ID (optional)
   * @param traceId WRC Trace Context traceId (optional)
   * @param principal the optional principal name for the event
   * @param rootFields an optional list of {@link AuditValue} objects that are represented at the event's root
   *     level
   * @param dataFields an optional list of {@link AuditValue} objects that are added to the {@code data} field of
   *     the event.
   */
  public AuditEvent(final @NonNull AuditType type, final @Nullable Instant timestamp,
      final @Nullable ApplicationName applicationName, final @Nullable CorrelationID correlationId,
      final @Nullable TraceID traceId, final @Nullable String principal,
      final @Nullable List<AuditValue<? extends Serializable>> rootFields,
      final @Nullable List<AuditValue<? extends Serializable>> dataFields) {
    super(Optional.ofNullable(timestamp).orElseGet(Instant::now), principal, type.type(), buildDataMap(dataFields));
    this.applicationName = applicationName;
    this.correlationId = correlationId;
    this.traceId = traceId;
    if (rootFields != null) {
      this.rootFields = new LinkedHashMap<>();
      rootFields.forEach(v -> this.rootFields.put(v.getName(), v.getValue()));
    }
    else {
      this.rootFields = Map.of();
    }
  }

  /**
   * Creates an {@link AuditEvent} from its parsed JSON representation. Used by Jackson when deserializing an audit
   * event.
   * <p>
   * Unlike {@link #AuditEvent(AuditType, Instant, ApplicationName, CorrelationID, TraceID, String, List, List)}, the
   * fields are assigned verbatim from the parsed representation, i.e., there is <em>no</em> fall back to the MDC for
   * the correlation ID. Any properties that are not part of the base structure are collected as
   * {@link #getRootFields() root fields}.
   * </p>
   *
   * @param type the audit event type
   * @param timestamp the timestamp (the current time is used if {@code null})
   * @param applicationName the application name (or {@code null})
   * @param correlationId the correlation ID (or {@code null})
   * @param traceId WRC Trace Context traceId (or {@code null})
   * @param principal the principal (or {@code null})
   * @param data the event data (or {@code null})
   * @return an {@link AuditEvent}
   */
  @JsonCreator
  static @NonNull AuditEvent fromJson(
      @JsonProperty("type") final @NonNull String type,
      @JsonProperty("timestamp") final @Nullable Instant timestamp,
      @JsonProperty("application_name") final @Nullable String applicationName,
      @JsonProperty("correlation_id") final @Nullable String correlationId,
      @JsonProperty("trace_id") final @Nullable String traceId,
      @JsonProperty("principal") final @Nullable String principal,
      @JsonProperty("data") final @Nullable Map<String, Object> data) {
    return new AuditEvent(type, timestamp, applicationName, correlationId, traceId, principal, data);
  }

  /**
   * Constructor assigning the fields verbatim from a parsed JSON representation. See {@link #fromJson}.
   *
   * @param type the audit event type
   * @param timestamp the timestamp (the current time is used if {@code null})
   * @param applicationName the application name (or {@code null})
   * @param correlationId the correlation ID (or {@code null})
   * @param traceId WRC Trace Context traceId (or {@code null})
   * @param principal the principal (or {@code null})
   * @param data the event data (or {@code null})
   */
  private AuditEvent(final @NonNull String type, final @Nullable Instant timestamp,
      final @Nullable String applicationName, final @Nullable String correlationId, final @Nullable String traceId,
      final @Nullable String principal, final @Nullable Map<String, Object> data) {
    super(Optional.ofNullable(timestamp).orElseGet(Instant::now), principal, type,
        data != null ? data : Map.of());
    this.applicationName = applicationName != null ? new ApplicationName(applicationName) : null;
    this.correlationId = correlationId != null ? new CorrelationID(correlationId) : null;
    this.traceId = traceId != null ? new TraceID(traceId) : null;
    this.rootFields = new LinkedHashMap<>();
  }

  /**
   * Collects a root-level field encountered during deserialization, i.e., a field that is not part of the base
   * structure. Used by Jackson together with {@link #getRootFields()}.
   *
   * @param name the field name
   * @param value the field value
   */
  @JsonAnySetter
  private void addRootField(final @NonNull String name, final @Nullable Serializable value) {
    this.rootFields.put(name, value);
  }

  private static Map<String, Object> buildDataMap(
      final @Nullable List<AuditValue<? extends Serializable>> dataObjects) {
    if (dataObjects != null) {
      final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
      dataObjects.forEach(v -> map.put(v.getName(), v.getValue()));
      return map;
    }
    else {
      return Map.of();
    }
  }

  /**
   * Gets the application name.
   *
   * @return the application name, or {@code null} if not available
   */
  public @Nullable ApplicationName getApplicationName() {
    return this.applicationName;
  }

  /**
   * Gets the correlation ID.
   *
   * @return the correlation ID, or {@code null} if not available
   */
  public @Nullable CorrelationID getCorrelationId() {
    return this.correlationId;
  }

  /**
   * Gets the <a href="https://www.w3.org/TR/trace-context/#trace-id"></a>W3C Trace Context trace-id</a>.
   *
   * @return the trace ID, or {@code null} if not available
   */
  public @Nullable TraceID getTraceId() {
    return this.traceId;
  }

  /**
   * Method for serialization of root fields.
   *
   * @return the root fields map
   */
  @JsonAnyGetter
  public @NonNull Map<String, Serializable> getRootFields() {
    return this.rootFields;
  }
}
