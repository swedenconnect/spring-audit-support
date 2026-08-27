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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.tracing.TraceID;
import se.swedenconnect.spring.audit.value.AuditValue;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * A builder for creating an {@link AuditEvent}.
 *
 * @author Martin Lindström
 */
public class AuditEventBuilder {

  /** The audit type. */
  private AuditType type;

  /** The timestamp. */
  private Instant timestamp;

  /** The principal. */
  private String principal;

  /** The application name. */
  private ApplicationName applicationName;

  /** The correlation ID. */
  private CorrelationID correlationId;

  /** The trace ID. */
  private TraceID traceId;

  /** Root fields. */
  private List<AuditValue<? extends Serializable>> rootFields;

  /** Data fields. */
  private List<AuditValue<? extends Serializable>> dataFields;

  /**
   * Default constructor.
   */
  public AuditEventBuilder() {
  }

  /**
   * Creates a builder based on an {@link AuditEventContext}.
   * <p>
   * The following fields are initialized from the context (if present):
   *   <ul>
   *     <li>{@code application_name}</li>
   *     <li>{@code correlation_id}</li>
   *     <li>{@code trace_id}</li>
   *     <li>{@code principal}</li>
   *   </ul>
   * </p>
   * <p>
   *   Note: Properties initialized from the context may be overridden by invoking builder methods.
   * </p>
   *
   * @param context the {@link AuditEventContext}
   */
  public AuditEventBuilder(final @NonNull AuditEventContext context) {
    this.applicationName = context.getApplicationName();
    this.correlationId = context.getCorrelationId();
    this.traceId = context.getTraceId();
    this.principal = context.getPrincipal();
  }

  /**
   * Creates a new (empty) {@link AuditEventBuilder}.
   *
   * @return a new {@link AuditEventBuilder}
   */
  public static @NonNull AuditEventBuilder builder() {
    return new AuditEventBuilder();
  }

  /**
   * Creates a new {@link AuditEventBuilder} that is initialized from the given context. See
   * {@link AuditEventBuilder#AuditEventBuilder(AuditEventContext)}.
   *
   * @return a new {@link AuditEventBuilder}
   */
  public static @NonNull AuditEventBuilder builder(final @NonNull AuditEventContext context) {
    return new AuditEventBuilder(context);
  }

  /**
   * Builds the {@link AuditEvent} from the assigned properties.
   *
   * @return an {@link AuditEvent}
   * @throws NullPointerException if no audit type has been assigned
   */
  public @NonNull AuditEvent build() throws NullPointerException {
    return new AuditEvent(Objects.requireNonNull(this.type, "type must be assigned"),
        this.timestamp, this.applicationName, this.correlationId, this.traceId, this.principal, this.rootFields,
        this.dataFields);
  }

  /**
   * Assigns the audit type.
   *
   * @param type the audit type
   * @return this builder
   * @throws NullPointerException if {@code type} is {@code null}
   */
  public @NonNull AuditEventBuilder type(final @NonNull AuditType type) throws NullPointerException {
    this.type = Objects.requireNonNull(type, "type must not be null");
    return this;
  }

  /**
   * Assigns the audit type given its string representation.
   *
   * @param type the audit type
   * @return this builder
   * @throws NullPointerException if {@code type} is {@code null}
   */
  public @NonNull AuditEventBuilder type(final @NonNull String type) throws NullPointerException {
    this.type = AuditType.of(Objects.requireNonNull(type, "type must not be null"));
    return this;
  }

  /**
   * Assigns the timestamp for the event. If not assigned, the current time will be used.
   *
   * @param timestamp the timestamp (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder timestamp(final @Nullable Instant timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  /**
   * Assigns the timestamp for the event. If not assigned, the current time will be used.
   *
   * @param timestamp the timestamp (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder timestamp(final @Nullable Date timestamp) {
    this.timestamp = timestamp != null ? timestamp.toInstant() : null;
    return this;
  }

  /**
   * Assigns the timestamp for the event by taking the current instant from the supplied {@link Clock}. If not assigned,
   * the current time will be used.
   *
   * @param timestamp the clock to obtain the timestamp from (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder timestamp(final @Nullable Clock timestamp) {
    this.timestamp = timestamp != null ? timestamp.instant() : null;
    return this;
  }

  /**
   * Assigns the principal (i.e., the initiator of the audited operation).
   *
   * @param principal the principal (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder principal(final @Nullable String principal) {
    this.principal = principal;
    return this;
  }

  /**
   * Assigns the name of the application that produced the event.
   *
   * @param applicationName the application name (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder applicationName(final @Nullable ApplicationName applicationName) {
    this.applicationName = applicationName;
    return this;
  }

  /**
   * Assigns the name of the application that produced the event, given its string representation.
   *
   * @param applicationName the application name (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder applicationName(final @Nullable String applicationName) {
    this.applicationName = applicationName != null ? new ApplicationName(applicationName) : null;
    return this;
  }

  /**
   * Assigns the correlation ID that ties the event to a specific flow or session.
   *
   * @param correlationId the correlation ID (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder correlationId(final @Nullable CorrelationID correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  /**
   * Assigns the correlation ID, given its string representation, that ties the event to a specific flow or session.
   *
   * @param correlationId the correlation ID (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder correlationId(final @Nullable String correlationId) {
    this.correlationId = correlationId != null ? new CorrelationID(correlationId) : null;
    return this;
  }

  /**
   * Assigns the trace ID.
   *
   * @param traceId the trace ID (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder traceId(final @Nullable TraceID traceId) {
    this.traceId = traceId;
    return this;
  }

  /**
   * Assigns the trace ID given its string representation.
   *
   * @param traceId the trace ID (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder traceId(final @Nullable String traceId) {
    this.traceId = traceId != null ? TraceID.of(traceId) : null;
    return this;
  }

  /**
   * Assigns the root fields of the event, replacing any previously assigned root fields.
   *
   * @param rootFields the root fields (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder rootFields(final @Nullable List<AuditValue<? extends Serializable>> rootFields) {
    this.rootFields = rootFields;
    return this;
  }

  /**
   * Adds a single root field to the event.
   *
   * @param rootField the root field to add
   * @return this builder
   */
  public @NonNull AuditEventBuilder rootField(final @NonNull AuditValue<? extends Serializable> rootField) {
    if (this.rootFields == null) {
      this.rootFields = new ArrayList<>();
    }
    this.rootFields.add(rootField);
    return this;
  }

  /**
   * Assigns the data fields of the event, replacing any previously assigned data fields.
   *
   * @param dataFields the data fields (or {@code null})
   * @return this builder
   */
  public @NonNull AuditEventBuilder dataFields(final @Nullable List<AuditValue<? extends Serializable>> dataFields) {
    this.dataFields = dataFields;
    return this;
  }

  /**
   * Adds a single data field to the event.
   *
   * @param dataField the data field to add
   * @return this builder
   */
  public @NonNull AuditEventBuilder dataField(final @NonNull AuditValue<? extends Serializable> dataField) {
    if (this.dataFields == null) {
      this.dataFields = new ArrayList<>();
    }
    this.dataFields.add(dataField);
    return this;
  }

}
