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
package se.swedenconnect.spring.audit.support;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representation of a generic correlation ID, i.e., an ID that is used to group audit events that belong to the same
 * flow or session.
 * <p>
 * A correlation ID is carried in the MDC (Mapped Diagnostic Context) under the {@link #MDC_KEY} key. Use
 * {@link #mdcPut()} to store it there, and {@link #fromMDC()} to read the correlation ID of the current thread.
 * </p>
 *
 * @author Martin Lindström
 */
public class CorrelationID implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(CorrelationID.class);

  /** The key of the CorrelationID when stored in MDC */
  public static final String MDC_KEY = "correlationID";

  /** The ID value. */
  @JsonValue
  private final String value;

  /**
   * Constructor.
   *
   * @param value the value of the ID
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
   * Creates an {@link CorrelationID} by reading the value from MDC.
   *
   * @return an {@link CorrelationID} or {@code null} if no value is available in MDC
   */
  public static @Nullable CorrelationID fromMDC() {
    try {
      return Optional.ofNullable(MDC.get(MDC_KEY))
          .map(CorrelationID::new)
          .orElse(null);
    }
    catch (final Exception e) {
      log.warn("Error querying MDC for CorrelationID", e);
      return null;
    }
  }

  /**
   * Stores this correlation ID's value in the MDC under the {@link #MDC_KEY} key.
   *
   * @throws RuntimeException if the value can not be added to the MDC
   */
  public void mdcPut() {
    try {
      MDC.put(MDC_KEY, this.getValue());
    }
    catch (final Exception e) {
      throw new RuntimeException("Failed to add CorrelationID to MDC", e);
    }
  }

  /**
   * Gets the value of the ID.
   *
   * @return the value
   */
  public @NonNull String getValue() {
    return this.value;
  }

  /**
   * Generates an {@link CorrelationID}.
   *
   * @return an {@link CorrelationID}
   */
  public static @NonNull CorrelationID generate() {
    return new CorrelationID(UUID.randomUUID().toString());
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
