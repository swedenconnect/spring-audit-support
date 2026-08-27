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
 * An interface for transforming an {@link ApplicationEvent} into an {@link AuditEvent}.
 *
 * @author Martin Lindström
 */
public interface EventTransformer {

  /**
   * Transforms the given event into an {@link AuditEvent}.
   *
   * @param event the event to transform
   * @param context the audit event context that the transformer may use when creating the {@link AuditEvent}
   * @return the {@link AuditEvent}
   * @throws UnsupportedOperationException is thrown if an event not supported by the transformer is supplied
   */
  @NonNull AuditEvent transform(final @NonNull ApplicationEvent event, final @NonNull AuditEventContext context)
      throws UnsupportedOperationException;

  /**
   * Predicate that tells whether this transformer supports the given event.
   *
   * @param event the event to test whether it can be transformed
   * @return {@code true} if the event can be transformed, {@code false} otherwise
   */
  boolean supports(final @NonNull ApplicationEvent event);

}
