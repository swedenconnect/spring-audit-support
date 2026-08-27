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

/**
 * An {@code AuditEventContextResolver} is responsible for populating an {@link AuditEventContext}.
 * <p>
 * A resolver is normally invoked once per audited event, and it is up to the implementation to decide from where the
 * context data is obtained. Normally, everything can be resolved from thread bound state (such as the MDC and the
 * Spring Security context), but an implementation may also make use of the (optional) input object supplied to
 * {@link #getContext(Object)}.
 * </p>
 * <p>
 * A resolver is invoked by the component that drives the transformation of application events into audit events, i.e.,
 * the component that hands the resolved {@link AuditEventContext} to the
 * {@link se.swedenconnect.spring.audit.transform.EventTransformer EventTransformer}s. In this library, that role is
 * held by the {@link AuditApplicationListener}.
 * </p>
 *
 * @author Martin Lindström
 */
public interface AuditEventContextResolver {

  /**
   * Resolves an {@link AuditEventContext}.
   * <p>
   * The {@code input} parameter makes it possible for a caller to supply state information that the implementation may
   * need in order to resolve the context, for example the {@code HttpServletRequest} being processed, or the event that
   * is being audited. Implementations that do not need any such information simply ignore the parameter, and callers
   * that have nothing to supply pass {@code null}.
   * </p>
   *
   * @param input optional state information for the resolver, or {@code null}
   * @return an {@link AuditEventContext}
   */
  @NonNull AuditEventContext getContext(final @Nullable Object input);

}
