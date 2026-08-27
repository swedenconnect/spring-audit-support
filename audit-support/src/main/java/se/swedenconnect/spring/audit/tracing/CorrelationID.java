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
import java.util.UUID;

/**
 * Representation of a generic correlation ID, i.e., an ID that is used to group audit events that belong to the same
 * flow.
 * <p>
 * A correlation ID may span several requests, and is assigned by the application itself based on its own logic - it may
 * be the ID of a received request, an operation ID supplied by a user interface, or a case number. This is what
 * separates it from a {@link TraceID}, which lives within one request and is never assigned by the application.
 * </p>
 * <p>
 * The correlation ID of the current flow is read and assigned using {@link CorrelationIDHolder}.
 * </p>
 *
 * @author Martin Lindström
 * @see TraceID
 * @see CorrelationIDHolder
 */
public class CorrelationID implements Serializable {

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
  public CorrelationID(final @NonNull String value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("value must not be empty");
    }
  }

  /**
   * Creates a {@link CorrelationID} given its string representation.
   *
   * @param value the correlation ID string representation
   * @return a {@link CorrelationID}
   */
  public static @NonNull CorrelationID of(final @NonNull String value) {
    return new CorrelationID(value);
  }

  /**
   * Generates a {@link CorrelationID}.
   *
   * @return a {@link CorrelationID}
   */
  public static @NonNull CorrelationID generate() {
    return new CorrelationID(UUID.randomUUID().toString());
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
    final CorrelationID other = (CorrelationID) obj;
    return Objects.equals(this.value, other.value);
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull String toString() {
    return this.value;
  }

}
