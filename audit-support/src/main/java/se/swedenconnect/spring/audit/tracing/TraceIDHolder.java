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

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The application facing entry point to the {@link TraceID} of the current request.
 * <p>
 * A trace ID lives within one request, but may span several services. It is assigned at the edge of a request, or by a
 * tracing framework, never by the application itself - which is why this holder only offers reading. The component that
 * assigns the trace ID uses {@link TraceIDWriter}. Compare with {@link CorrelationIDHolder}, which an application both
 * reads and writes, since a correlation ID is the application's own.
 * </p>
 * <p>
 * The holder is static so that an application can reach the trace ID from anywhere without having a bean injected.
 * Where the value is actually kept is decided by the installed {@link IdentifierStorage}, see
 * {@link IdentifierStorageHolder}.
 * </p>
 *
 * @author Martin Lindström
 * @see TraceID
 * @see CorrelationIDHolder
 */
public class TraceIDHolder {

  /**
   * The key under which the trace ID is stored.
   * <p>
   * The key is deliberately <em>not</em> {@code traceId}, which is the key Micrometer Tracing uses for the trace ID it
   * manages. Keeping the keys apart means that this library and a tracing framework can be used in the same
   * application without overwriting each other's values, and that a log pattern referring to one of them is
   * unambiguous. It is not {@code traceID} either, since a key differing from Micrometer's only in the case of a
   * single letter is a trap for whoever writes that log pattern.
   * </p>
   */
  public static final String TRACE_ID_KEY = "auditTraceID";

  /**
   * Gets the trace ID of the current request.
   *
   * @return the {@link TraceID}, or {@code null} if none has been assigned
   */
  public static @Nullable TraceID get() {
    return Optional.ofNullable(IdentifierStorageHolder.getStorage().get(TRACE_ID_KEY))
        .filter(v -> !v.isBlank())
        .map(TraceID::of)
        .orElse(null);
  }

  // Hidden constructor
  private TraceIDHolder() {
  }

}
