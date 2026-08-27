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

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Representation of a trace ID, i.e., the <a href="https://www.w3.org/TR/trace-context/#trace-id">W3C Trace Context</a>
 * {@code trace-id} that groups the audit events of one request across the services that handle it.
 * <p>
 * A trace ID lives within one request, but may span several services. It is assigned at the edge of a request, or by a
 * tracing framework, which is why an application only reads it - see {@link TraceIDHolder}. This is what separates it
 * from a {@link CorrelationID}, which the application assigns itself and which may span several requests.
 * </p>
 * <p>
 * Note that no assumptions are made about the format of the value. Even though a trace ID assigned according to the W3C
 * Trace Context specification is a 32 character hexadecimal string, a value produced by another tracing framework may
 * look different, and rejecting it would lose information that is useful in the audit log.
 * </p>
 *
 * @author Martin Lindström
 * @see CorrelationID
 * @see TraceIDHolder
 */
public class TraceID implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** The ID value. */
  @JsonValue
  private final String value;

  /**
   * Constructor.
   *
   * @param value the value of the ID
   * @throws NullPointerException if {@code value} is {@code null}
   * @throws IllegalArgumentException if {@code value} is empty
   */
  public TraceID(final @NonNull String value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("value must not be empty");
    }
  }

  /**
   * Creates a {@link TraceID} given its string representation.
   *
   * @param value the trace ID string representation
   * @return a {@link TraceID}
   */
  public static @NonNull TraceID of(final @NonNull String value) {
    return new TraceID(value);
  }

  /**
   * Gets the value of the ID.
   *
   * @return the value
   */
  public @NonNull String getValue() {
    return this.value;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(this.value);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(final @Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || (this.getClass() != obj.getClass())) {
      return false;
    }
    final TraceID other = (TraceID) obj;
    return Objects.equals(this.value, other.value);
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull String toString() {
    return this.value;
  }

}
