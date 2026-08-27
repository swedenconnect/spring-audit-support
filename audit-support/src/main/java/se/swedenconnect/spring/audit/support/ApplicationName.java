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
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Representation of an application name.
 *
 * @author Martin Lindström
 */
public class ApplicationName implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** The application name. */
  @JsonValue
  private final String name;

  /**
   * Constructor.
   *
   * @param name the application name
   */
  public ApplicationName(final @NonNull String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
  }

  /**
   * Gets the application name.
   *
   * @return the application name
   */
  public @NonNull String getName() {
    return this.name;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }
    final ApplicationName that = (ApplicationName) o;
    return Objects.equals(this.name, that.name);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hashCode(this.name);
  }

  /** {@inheritDoc} */
  public @NonNull String toString() {
    return this.name;
  }

}
