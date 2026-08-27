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
package se.swedenconnect.spring.audit.transform;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.ApplicationEvent;
import se.swedenconnect.spring.audit.AuditEventContext;

/**
 * A specialization of {@link EventTransformer} for transformers that handle a single, specific {@link ApplicationEvent}
 * class.
 * <p>
 * Implement this interface instead of {@link EventTransformer} when a transformer is only concerned with one specific
 * event class. Implementors only need to supply the event class via {@link #getEventType()} and the transformation
 * logic via {@link #transformEvent(ApplicationEvent, AuditEventContext)}; the {@link #supports(ApplicationEvent)} and
 * {@link #transform(ApplicationEvent, AuditEventContext)} methods are then handled by this interface.
 * </p>
 *
 * @param <T> the type of {@link ApplicationEvent} handled by the transformer
 * @author Martin Lindström
 */
public interface SingleEventTransformer<T extends ApplicationEvent> extends EventTransformer {

  /**
   * Transforms the given event into an {@link AuditEvent}.
   *
   * @param event the event to transform
   * @param context the audit event context that the transformer may use when creating the {@link AuditEvent}
   * @return the {@link AuditEvent}
   */
  @NonNull AuditEvent transformEvent(final @NonNull T event, final @NonNull AuditEventContext context);

  /**
   * Gets the event class handled by this transformer.
   *
   * @return the event class
   */
  @NonNull Class<T> getEventType();

  /**
   * Transforms the given event into an {@link AuditEvent} by delegating to
   * {@link #transformEvent(ApplicationEvent, AuditEventContext)} after verifying that the event is
   * {@link #supports(ApplicationEvent) supported}.
   *
   * @param event the event to transform
   * @param context the audit event context that the transformer may use when creating the {@link AuditEvent}
   * @return the {@link AuditEvent}
   * @throws UnsupportedOperationException if the event is not supported by this transformer
   */
  default @NonNull AuditEvent transform(final @NonNull ApplicationEvent event, final @NonNull AuditEventContext context)
      throws UnsupportedOperationException {
    if (!this.supports(event)) {
      throw new UnsupportedOperationException(
          "%s can not be transformed by this transformer".formatted(event.getClass().getName()));
    }
    return this.transformEvent(this.getEventType().cast(event), context);
  }

  /**
   * Tells whether this transformer supports the given event, i.e., whether the event is an instance of the
   * {@link #getEventType() event class} handled by this transformer.
   *
   * @param event the event to test
   * @return {@code true} if the event can be transformed, {@code false} otherwise
   */
  default boolean supports(final @NonNull ApplicationEvent event) {
    return this.getEventType().isInstance(event);
  }

}
