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
 * Representation of an application version, i.e., the version of the build that produced an audit event.
 * <p>
 * Unlike the {@link ApplicationName application name}, the version is optional. An application that can not determine
 * its version simply produces audit events without one.
 * </p>
 *
 * @author Martin Lindström
 */
public class ApplicationVersion implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** The application version. */
  @JsonValue
  private final String version;

  /**
   * Constructor.
   *
   * @param version the application version
   */
  public ApplicationVersion(final @NonNull String version) {
    this.version = Objects.requireNonNull(version, "version must not be null");
  }

  /**
   * Gets the application version.
   *
   * @return the application version
   */
  public @NonNull String getVersion() {
    return this.version;
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
    final ApplicationVersion that = (ApplicationVersion) o;
    return Objects.equals(this.version, that.version);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hashCode(this.version);
  }

  /** {@inheritDoc} */
  public @NonNull String toString() {
    return this.version;
  }

}
