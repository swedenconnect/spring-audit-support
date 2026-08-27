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
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The application facing entry point to the {@link CorrelationID} of the current flow.
 * <p>
 * A correlation ID may span several requests and is assigned by the application itself, based on its own logic - it may
 * be the ID of a received request, an operation ID supplied by a user interface, or a case number. Therefore, this
 * holder offers both reading and writing, unlike {@link TraceIDHolder}, which an application only reads from.
 * </p>
 * <p>
 * The holder is static so that an application can reach the correlation ID from anywhere without having a bean injected
 * - the service class that parses a request and knows the ID to use is typically far down the call chain. Where the
 * value is actually kept is decided by the installed {@link IdentifierStorage}, see {@link IdentifierStorageHolder}.
 * </p>
 * <p>
 * Assigning a correlation ID never fails. If the installed storage has nowhere to keep the value - which can happen
 * under a storage bound to a request scope, but never under the default
 * {@link MdcIdentifierStorage MDC storage} - the value is dropped and the storage logs it. The audit events of that
 * flow are then written without a correlation ID, which is preferable to failing the operation being audited for the
 * sake of a diagnostic identifier.
 * </p>
 *
 * @author Martin Lindström
 * @see CorrelationID
 * @see TraceIDHolder
 */
public class CorrelationIDHolder {

  /**
   * The key under which the correlation ID is stored.
   * <p>
   * The key is also the name the correlation ID appears under in the application's ordinary log records when the
   * default {@link MdcIdentifierStorage MDC storage} is used.
   * </p>
   */
  public static final String CORRELATION_ID_KEY = "correlationID";

  /**
   * Gets the correlation ID of the current flow.
   *
   * @return the {@link CorrelationID}, or {@code null} if none has been assigned
   */
  public static @Nullable CorrelationID get() {
    return Optional.ofNullable(IdentifierStorageHolder.getStorage().get(CORRELATION_ID_KEY))
        .filter(v -> !v.isBlank())
        .map(CorrelationID::of)
        .orElse(null);
  }

  /**
   * Assigns the correlation ID of the current flow, replacing any correlation ID previously assigned.
   *
   * @param correlationId the correlation ID to assign
   * @throws NullPointerException if {@code correlationId} is {@code null} - use {@link #clear()} to
   *     remove the correlation ID of the current flow
   */
  public static void set(final @NonNull CorrelationID correlationId) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    IdentifierStorageHolder.getStorage().put(CORRELATION_ID_KEY, correlationId.getValue());
  }

  /**
   * Removes the correlation ID of the current flow. Never throws, so that it is safe to call from a {@code finally}
   * block.
   */
  public static void clear() {
    IdentifierStorageHolder.getStorage().clear(CORRELATION_ID_KEY);
  }

  // Hidden constructor
  private CorrelationIDHolder() {
  }

}
