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
import org.springframework.context.event.ContextClosedEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.AuditEventContext;

import java.time.Instant;

/**
 * An {@link EventTransformer} that transforms an {@link ContextClosedEvent} into an
 * {@link se.swedenconnect.spring.audit.AuditEvent AuditEvent} of the type {@code system_shutdown}, which is used to
 * audit that a system has shut down.
 *
 * @author Martin Lindström
 */
public class ContextClosedEventTransformer implements SingleEventTransformer<ContextClosedEvent> {

  /** {@inheritDoc} */
  @Override
  public @NonNull AuditEvent transformEvent(
      final @NonNull ContextClosedEvent event, final @NonNull AuditEventContext context) {
    return AuditEventBuilder.builder(context)
        .type("system_shutdown")
        .timestamp(Instant.ofEpochMilli(event.getTimestamp()))
        .principal(se.swedenconnect.spring.audit.AuditEvent.SYSTEM_PRINCIPAL)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull Class<ContextClosedEvent> getEventType() {
    return ContextClosedEvent.class;
  }
}
