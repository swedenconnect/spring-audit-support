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
package se.swedenconnect.spring.audit.appevents;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.ApplicationEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.AuditEventContext;
import se.swedenconnect.spring.audit.LibraryVersion;
import se.swedenconnect.spring.audit.transform.SingleEventTransformer;
import se.swedenconnect.spring.audit.value.MapAuditValue;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An {@link ApplicationEvent} that an application publishes to raise a <em>system alert</em> &ndash; an operational
 * condition (typically an error or anomaly) that operators should be made aware of. The event is picked up by the audit
 * infrastructure and {@link #transformEvent(SystemAlertEvent, AuditEventContext) transformed} into a structured
 * {@link se.swedenconnect.spring.audit.AuditEvent} of type {@code system_alert} that is written to the audit log.
 * <p>
 * Note: Since the event class implements {@link se.swedenconnect.spring.audit.transform.EventTransformer} there is no
 * need to explicitly register the event transformation in
 * {@link se.swedenconnect.spring.audit.AuditApplicationListener}.
 * </p>
 * <p>
 * The audit event carries the {@linkplain se.swedenconnect.spring.audit.AuditEvent base audit event structure} (type,
 * timestamp, application name, correlation ID and principal). Since a system alert is normally not tied to an end user,
 * the system principal ({@value se.swedenconnect.spring.audit.AuditEvent#SYSTEM_PRINCIPAL}) is used when no principal
 * is available. The event-specific content is carried in the {@code data.alert_info} object:
 * </p>
 * <table border="1">
 *   <caption>{@code alert_info} members</caption>
 *   <thead>
 *     <tr><th>Member</th><th>Content</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>{@code message}</td><td>The human-readable alert message. Always present.</td></tr>
 *     <tr><td>{@code exception_class}</td>
 *         <td>The fully qualified class name of the {@link #getException() associated exception}, or {@code null} if the
 *         alert was raised without an exception.</td></tr>
 *     <tr><td>{@code exception_message}</td>
 *         <td>The {@link Throwable#getMessage() message} of the associated exception, or {@code null}.</td></tr>
 *   </tbody>
 * </table>
 * <p>
 * See {@link se.swedenconnect.spring.audit.AuditEvent} for the documentation of the base fields and a full JSON
 * example.
 * </p>
 *
 * @author Martin Lindström
 * @see se.swedenconnect.spring.audit.AuditEvent
 */
public class SystemAlertEvent extends ApplicationEvent implements SingleEventTransformer<SystemAlertEvent> {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Creates a system alert without an associated exception.
   *
   * @param message the alert message
   * @throws NullPointerException if {@code message} is {@code null}
   */
  public SystemAlertEvent(final @NonNull String message) throws NullPointerException {
    this(message, null);
  }

  /**
   * Creates a system alert with an associated exception.
   *
   * @param message the alert message
   * @param exception the exception that triggered the alert (or {@code null})
   * @throws NullPointerException if {@code message} is {@code null}
   */
  public SystemAlertEvent(final @NonNull String message, final @Nullable Exception exception)
      throws NullPointerException {
    super(new AlertData(Objects.requireNonNull(message, "message must not be null"), exception));
  }

  /**
   * Gets the alert message.
   *
   * @return the alert message
   */
  public @NonNull String getMessage() {
    return ((AlertData) this.getSource()).message();
  }

  /**
   * Gets the exception that triggered the alert, if any.
   *
   * @return the associated exception, or {@code null} if the alert was raised without an exception
   */
  public @Nullable Exception getException() {
    return ((AlertData) this.getSource()).exception();
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull AuditEvent transformEvent(
      final @NonNull SystemAlertEvent event, final @NonNull AuditEventContext context) {

    return AuditEventBuilder.builder(context)
        .type("system_alert")
        .timestamp(Instant.ofEpochMilli(event.getTimestamp()))
        .dataField(MapAuditValue.builder()
            .name("alert_info")
            .value("message", this.getMessage())
            .value("exception_class", Optional.ofNullable(this.getException())
                .map(c -> c.getClass().getName())
                .orElse(null))
            .value("exception_message", Optional.ofNullable(this.getException())
                .map(Throwable::getMessage)
                .orElse(null))
            .build())
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull Class<SystemAlertEvent> getEventType() {
    return SystemAlertEvent.class;
  }

  // For storing the alert info
  private record AlertData(@NonNull String message, @Nullable Exception exception) implements Serializable {
  }

}
