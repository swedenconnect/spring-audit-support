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
package se.swedenconnect.spring.audit.tracing;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Assigns the {@link TraceID} of the current request.
 * <p>
 * <b>This class is not part of the application facing API.</b> It exists for the infrastructure components that own the
 * trace ID - an HTTP filter that reads the {@code traceparent} header of an incoming request, or an integration with a
 * tracing framework. Application code reads the trace ID through {@link TraceIDHolder} and does not assign it: a trace
 * ID that the application invents itself is not the trace ID of the request, and writing one would break the link to
 * the other services handling it.
 * </p>
 * <p>
 * The write path is a separate type rather than a method on {@link TraceIDHolder} precisely so that it is absent from
 * the surface an application sees and completes with. Since the components that need it live in other modules, no
 * access modifier can express the restriction - keeping it in a class of its own, named and documented for its
 * purpose, is what makes the intent unambiguous.
 * </p>
 *
 * @author Martin Lindström
 * @see TraceIDHolder
 */
public class TraceIDWriter {

  /**
   * Assigns the trace ID of the current request, replacing any trace ID previously assigned.
   *
   * @param traceId the trace ID to assign
   * @throws NullPointerException if {@code traceId} is {@code null} - use {@link #clear()} to remove the trace
   *     ID of the current request
   */
  public static void set(final @NonNull TraceID traceId) {
    Objects.requireNonNull(traceId, "traceId must not be null");
    IdentifierStorageHolder.getStorage().put(TraceIDHolder.TRACE_ID_KEY, traceId.getValue());
  }

  /**
   * Removes the trace ID of the current request. Never throws, so that it is safe to call from a {@code finally}
   * block - which is where a filter that assigned a trace ID removes it again.
   */
  public static void clear() {
    IdentifierStorageHolder.getStorage().clear(TraceIDHolder.TRACE_ID_KEY);
  }

  // Hidden constructor
  private TraceIDWriter() {
  }

}
