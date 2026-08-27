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
package se.swedenconnect.spring.audit.value;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.LibraryVersion;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * An {@code AuditValue} is an object that is assigned to an {@link AuditEvent} and is identified by a name and has a
 * value.
 *
 * @author Martin Lindström
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuditValue<T extends Serializable> implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /** The name of the audit value. */
  private final String name;

  /** The value of the audit value. */
  private final T value;

  /**
   * Constructor.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public AuditValue(final @NonNull String name, final @Nullable T value) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.value = value;
  }

  /**
   * Creates a new builder for creating an {@link AuditValue}.
   * <p>
   * The returned type is the {@link AbstractBuilder} base type rather than the concrete {@link Builder} so that the
   * subclasses ({@link se.swedenconnect.spring.audit.value.MapAuditValue MapAuditValue} and
   * {@link se.swedenconnect.spring.audit.value.ListAuditValue ListAuditValue}) may declare their own covariant
   * {@code builder()} factories.
   * </p>
   *
   * @param <T> the audit value type
   * @return a new builder
   */
  public static <T extends Serializable> @NonNull AbstractBuilder<T, ?> builder() {
    return new Builder<>();
  }

  /**
   * Gets the audit value name.
   *
   * @return the audit value name
   */
  public @NonNull String getName() {
    return this.name;
  }

  /**
   * Gets the value of the audit value.
   *
   * @return the value of the audit value
   */
  public @Nullable T getValue() {
    return this.value;
  }

  /**
   * Returns the object as a map. This is used for JSON serialization.
   *
   * @return the object as a map
   */
  @JsonValue
  public @NonNull Map<String, T> toMap() {
    return Collections.singletonMap(this.name, this.value);
  }

  @Override
  public String toString() {
    return "%s=%s".formatted(this.name, this.value);
  }

  /**
   * An abstract base builder for {@link AuditValue} subclasses. It manages the audit value name and uses a self-type
   * parameter so that the fluent methods declared here return the concrete builder type.
   *
   * @param <T> the audit value type
   * @param <B> the concrete builder type (self-type)
   * @author Martin Lindström
   */
  public abstract static class AbstractBuilder<T extends Serializable, B extends AbstractBuilder<T, B>> {

    /** The name of the audit value. */
    protected String name;

    /**
     * Returns this builder as its concrete type. Subclasses return {@code this}.
     *
     * @return this builder
     */
    protected abstract @NonNull B self();

    /**
     * Builds the {@link AuditValue} from the assigned properties.
     *
     * @return an {@link AuditValue}
     * @throws NullPointerException if no name has been assigned
     */
    public abstract @NonNull AuditValue<T> build() throws NullPointerException;

    /**
     * Assigns the name of the audit value.
     *
     * @param name the name of the audit value
     * @return this builder
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public @NonNull B name(final @NonNull String name) throws NullPointerException {
      this.name = Objects.requireNonNull(name, "name must not be null");
      return this.self();
    }

    /**
     * Assigns the value of the audit value, replacing any previously assigned value.
     *
     * @param value the value of the audit value (may be {@code null})
     * @return this builder
     */
    public abstract @NonNull B value(final @Nullable T value);

  }

  /**
   * A builder for creating an {@link AuditValue}.
   *
   * @param <T> the audit value type
   * @author Martin Lindström
   */
  public static class Builder<T extends Serializable> extends AbstractBuilder<T, Builder<T>> {

    /** The value of the audit value. */
    private @Nullable T value;

    /**
     * Constructor.
     */
    private Builder() {
    }

    /** {@inheritDoc} */
    @Override
    protected @NonNull Builder<T> self() {
      return this;
    }

    /**
     * Builds the {@link AuditValue} from the assigned properties.
     *
     * @return an {@link AuditValue}
     * @throws NullPointerException if no name has been assigned
     */
    @Override
    public @NonNull AuditValue<T> build() throws NullPointerException {
      return new AuditValue<>(Objects.requireNonNull(this.name, "name must be assigned"), this.value);
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull Builder<T> value(final @Nullable T value) {
      this.value = value;
      return this;
    }

  }

}
