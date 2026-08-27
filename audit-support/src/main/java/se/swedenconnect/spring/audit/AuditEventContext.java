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

import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;

/**
 * Context that may be used by an {@link se.swedenconnect.spring.audit.transform.EventTransformer EventTransformer} when
 * building an {@link AuditEvent}. It carries the ambient information (correlation ID, trace ID, application name, and
 * principal) that may not be part of the source event itself.
 * <p>
 * A transformer that creates its audit event using an {@link AuditEventBuilder} may hand the context to the builder,
 * see {@link AuditEventBuilder#builder(AuditEventContext)}. The builder is then initialized with the values of the
 * context, each of which may be overridden by invoking the corresponding builder method.
 * </p>
 *
 * @author Martin Lindström
 */
public interface AuditEventContext {

  /**
   * Gets the name of the application that produced the event.
   *
   * @return the application name, or {@code null}
   */
  @Nullable ApplicationName getApplicationName();

  /**
   * Gets the correlation ID that ties the event to a specific flow or session.
   *
   * @return the correlation ID, or {@code null}
   */
  @Nullable CorrelationID getCorrelationId();

  /**
   * If the application uses a framework that implements the <a href="https://www.w3.org/TR/trace-context/">W3C Trace
   * Context</a>, this method may return the "trace-id", which is a per request distributed ID within a system (multiple
   * applications).
   * <p>
   * The difference between a correlation ID and a trace ID is that a correlation ID may be used to group events that
   * span over multiple requests, and a trace ID is used to group events for one request.
   * </p>
   *
   * @return the trace ID, or {@code null}
   */
  @Nullable String getTraceId();

  /**
   * Gets the principal, i.e., the initiator of the audited operation.
   *
   * @return the principal, or {@code null}
   */
  @Nullable String getPrincipal();

}
