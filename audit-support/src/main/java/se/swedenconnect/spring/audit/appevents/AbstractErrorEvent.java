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
import se.swedenconnect.spring.audit.AuditType;
import se.swedenconnect.spring.audit.LibraryVersion;
import se.swedenconnect.spring.audit.transform.EventTransformer;
import se.swedenconnect.spring.audit.value.AuditValueConstants;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A base class for {@link ApplicationEvent}s that represent errors, i.e., events that an application publishes when
 * something has gone wrong and this should be recorded in the audit log.
 * <p>
 * The error itself is described by the {@link Error} record - an error code, and optionally a message, the class of the
 * exception that caused the error, and further details. This information is carried in the {@code data.error} object of
 * the resulting {@link se.swedenconnect.spring.audit.AuditEvent AuditEvent}:
 * </p>
 * <table border="1">
 *   <caption>{@code error} members</caption>
 *   <thead>
 *     <tr><th>Member</th><th>Content</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>{@code code}</td><td>The error code. Always present.</td></tr>
 *     <tr><td>{@code message}</td><td>The human-readable error message, or {@code null}.</td></tr>
 *     <tr><td>{@code exception_class}</td>
 *         <td>The fully qualified class name of the exception that caused the error, or {@code null}.</td></tr>
 *     <tr><td>{@code details}</td><td>Further details about the error, or {@code null}.</td></tr>
 *   </tbody>
 * </table>
 * <p>
 * Since the class implements {@link EventTransformer}, an error event transforms itself into an audit event. An
 * application therefore only needs to publish the event - there is no need to register a transformer with the
 * {@link se.swedenconnect.spring.audit.AuditApplicationListener AuditApplicationListener}.
 * </p>
 * <p>
 * A subclass supplies the audit event type by implementing {@link #getAuditType()}, and completes the audit event by
 * implementing {@link #transform(AuditEventBuilder, AuditEventContext)}, where it may add its own data fields before
 * building the event.
 * </p>
 *
 * @author Martin Lindström
 * @see se.swedenconnect.spring.audit.AuditEvent
 */
public abstract class AbstractErrorEvent extends ApplicationEvent implements EventTransformer {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Constructor.
   *
   * @param code the error code
   * @param message the error message, or {@code null}
   * @param exception the class of the exception that caused the error, or {@code null}
   * @param details further details about the error, or {@code null}
   * @throws NullPointerException if {@code code} is {@code null}
   */
  public AbstractErrorEvent(final @NonNull String code, final @Nullable String message,
      final @Nullable Class<?> exception, final @Nullable String details) {
    super(new Error(Objects.requireNonNull(code, "code must not be null"), message, exception, details));
  }

  /**
   * Gets the error that this event represents.
   *
   * @return an {@link Error}
   */
  public @NonNull Error getError() {
    return (Error) this.getSource();
  }

  /**
   * Transforms this event into an {@link se.swedenconnect.spring.audit.AuditEvent AuditEvent} of the type given by
   * {@link #getAuditType()}.
   * <p>
   * The audit event is given the timestamp of the supplied event, the {@code data.error} object holding this event's
   * {@link #getError() error}, and the application name, correlation ID, trace ID and principal from the supplied
   * context. The event is then handed to {@link #transform(AuditEventBuilder, AuditEventContext)} where the subclass
   * completes it.
   * </p>
   *
   * @param event the event to transform
   * @param context the audit event context
   * @return an {@link AuditEvent}
   * @throws UnsupportedOperationException if the supplied event is not an {@link AbstractErrorEvent}
   */
  @Override
  public @NonNull AuditEvent transform(final @NonNull ApplicationEvent event, final @NonNull AuditEventContext context)
      throws UnsupportedOperationException {
    if (!this.supports(event)) {
      throw new UnsupportedOperationException(
          "%s can not be transformed by this transformer".formatted(this.getClass().getSimpleName()));
    }
    final AuditEventBuilder builder = AuditEventBuilder.builder(context)
        .type(this.getAuditType())
        .timestamp(Instant.ofEpochMilli(event.getTimestamp()))
        .dataField(AuditValueConstants.error(
            this.getError().code(), this.getError().message(), this.getError().exception(), this.getError().details()));
    return this.transform(builder, context);
  }

  /**
   * Completes the transformation of this event into an {@link se.swedenconnect.spring.audit.AuditEvent AuditEvent}.
   * <p>
   * The supplied builder has already been initialized with the audit type, the timestamp, the {@code data.error}
   * object, and the values of the context. A subclass may assign further fields before invoking
   * {@link AuditEventBuilder#build()}.
   * </p>
   *
   * @param eventBuilder the builder initialized with the values described above
   * @param context the audit event context
   * @return an {@link AuditEvent}
   */
  protected abstract @NonNull AuditEvent transform(
      final @NonNull AuditEventBuilder eventBuilder, final @NonNull AuditEventContext context);

  /**
   * Tells which {@link AuditType} that should be used when transforming this event into an
   * {@link se.swedenconnect.spring.audit.AuditEvent AuditEvent}.
   *
   * @return an {@link AuditType}
   */
  protected abstract @NonNull AuditType getAuditType();

  /**
   * Tells whether this transformer supports the given event, i.e., whether it is an {@link AbstractErrorEvent}.
   *
   * @param event the event to test
   * @return {@code true} if the event is an {@link AbstractErrorEvent}, and {@code false} otherwise
   */
  @Override
  public boolean supports(final @NonNull ApplicationEvent event) {
    return event instanceof AbstractErrorEvent;
  }

  /**
   * A representation of the error that an {@link AbstractErrorEvent} reports.
   *
   * @param code the error code
   * @param message the error message, or {@code null}
   * @param exception the class of the exception that caused the error, or {@code null}
   * @param details further details about the error, or {@code null}
   */
  public record Error(
      @NonNull String code, @Nullable String message, @Nullable Class<?> exception, @Nullable String details) {
  }

}
