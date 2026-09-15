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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Representation of the application that produced an audit event, i.e., its {@link ApplicationName name} and
 * {@link ApplicationVersion version}.
 * <p>
 * Both values are optional. An application object holding neither is {@link #isEmpty() empty}, and an audit event
 * created with an empty application object carries no application at all.
 * </p>
 * <p>
 * The serialized form holds the values that are present:
 * </p>
 * <pre>
 * "application": {
 *   "name": "my-service",
 *   "version": "1.2.3"
 * }
 * </pre>
 *
 * @author Martin Lindström
 */
@JsonPropertyOrder({ "name", "version" })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Application implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** The application name. */
  @JsonProperty(value = "name", required = false)
  private final ApplicationName name;

  /** The application version. */
  @JsonProperty(value = "version", required = false)
  private final ApplicationVersion version;

  /**
   * Constructor.
   *
   * @param name the application name (or {@code null})
   * @param version the application version (or {@code null})
   */
  public Application(final @Nullable ApplicationName name, final @Nullable ApplicationVersion version) {
    this.name = name;
    this.version = version;
  }

  /**
   * Creates an {@link Application} from its parsed JSON representation. Used by Jackson when deserializing an audit
   * event.
   *
   * @param name the application name (or {@code null})
   * @param version the application version (or {@code null})
   * @return an {@link Application}
   */
  @JsonCreator
  static @NonNull Application fromJson(
      @JsonProperty("name") final @Nullable String name,
      @JsonProperty("version") final @Nullable String version) {
    return new Application(
        name != null ? new ApplicationName(name) : null,
        version != null ? new ApplicationVersion(version) : null);
  }

  /**
   * Gets the application name.
   *
   * @return the application name, or {@code null} if not available
   */
  public @Nullable ApplicationName getName() {
    return this.name;
  }

  /**
   * Gets the application version.
   *
   * @return the application version, or {@code null} if not available
   */
  public @Nullable ApplicationVersion getVersion() {
    return this.version;
  }

  /**
   * Predicate that tells whether neither a name nor a version is available.
   *
   * @return {@code true} if the object holds no values at all, and {@code false} otherwise
   */
  @JsonIgnore
  public boolean isEmpty() {
    return this.name == null && this.version == null;
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
    final Application that = (Application) o;
    return Objects.equals(this.name, that.name) && Objects.equals(this.version, that.version);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(this.name, this.version);
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull String toString() {
    if (this.version == null) {
      return this.name != null ? this.name.toString() : "";
    }
    return this.name != null ? "%s-%s".formatted(this.name, this.version) : this.version.toString();
  }

}
