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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A specialization of an {@link AuditValue} that holds a {@link Map} as its value, i.e., the audit value represents an
 * object of values.
 *
 * @author Martin Lindström
 */
public class MapAuditValue extends AuditValue<LinkedHashMap<String, ? extends Serializable>> {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Constructor accepting a {@link Map} as the audit value.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public MapAuditValue(final @NonNull String name, final @Nullable Map<String, ? extends Serializable> value) {
    super(name, value != null ? new LinkedHashMap<>(value) : null);
  }

  /**
   * Constructor accepting a set of {@link AuditValue}s that are placed in a map where each {@link AuditValue}'s value
   * is the key in the map.
   *
   * @param name the name of the audit value
   * @param values the values that should be added to the map (may be {@code null})
   */
  @SafeVarargs
  public MapAuditValue(final @NonNull String name, final @Nullable AuditValue<? extends Serializable>... values) {
    super(name, createMap(values));
  }

  /**
   * Creates a new {@link Builder} for creating a {@link MapAuditValue}.
   *
   * @return a new {@link Builder}
   */
  public static @NonNull Builder builder() {
    return new Builder();
  }

  /**
   * Creates a {@link LinkedHashMap} given the {@link AuditValue} arguments.
   *
   * @param values the values that should be added to the map (may be {@code null})
   * @return a {@link LinkedHashMap}
   */
  @SafeVarargs
  protected static @Nullable LinkedHashMap<String, ? extends Serializable> createMap(
      final @Nullable AuditValue<? extends Serializable>... values) {
    return values != null
        ? Arrays.stream(values).collect(LinkedHashMap::new,
        (map, value) -> map.put(value.getName(), value.getValue()),
        LinkedHashMap::putAll)
        : null;
  }

  /**
   * Gets the value of the named member of the {@link AuditValue}.
   *
   * @param name the name of the member
   * @param type the type of the sub value
   * @param <T> the type of the sub value
   * @return the value, or {@code null} if no value exists
   */
  protected <T extends Serializable> @Nullable T getSubValue(final @NonNull String name, final @NonNull Class<T> type) {
    return Optional.ofNullable(this.getValue())
        .map(LinkedHashMap.class::cast)
        .map(map -> map.get(name))
        .filter(Objects::nonNull)
        .map(type::cast)
        .orElse(null);
  }

  /**
   * A builder for creating a {@link MapAuditValue}.
   *
   * @author Martin Lindström
   */
  public static class Builder
      extends AuditValue.AbstractBuilder<LinkedHashMap<String, ? extends Serializable>, Builder> {

    /** The members of the map. */
    private final LinkedHashMap<String, Serializable> values = new LinkedHashMap<>();

    /**
     * Constructor.
     */
    private Builder() {
    }

    /** {@inheritDoc} */
    @Override
    protected @NonNull Builder self() {
      return this;
    }

    /**
     * Builds the {@link MapAuditValue} from the assigned properties.
     *
     * @return a {@link MapAuditValue}
     * @throws NullPointerException if no name has been assigned
     */
    @Override
    public @NonNull MapAuditValue build() throws NullPointerException {
      return new MapAuditValue(Objects.requireNonNull(this.name, "name must be assigned"), this.values);
    }

    /**
     * Assigns the members of the map, replacing any previously added members. Equivalent to
     * {@link #values(Map)}.
     *
     * @param value the members (or {@code null})
     * @return this builder
     */
    @Override
    public @NonNull Builder value(final @Nullable LinkedHashMap<String, ? extends Serializable> value) {
      return this.values(value);
    }

    /**
     * Assigns the members of the map, replacing any previously added members.
     *
     * @param values the members (or {@code null})
     * @return this builder
     */
    public @NonNull Builder values(final @Nullable Map<String, ? extends Serializable> values) {
      this.values.clear();
      if (values != null) {
        this.values.putAll(values);
      }
      return this;
    }

    /**
     * Adds a member to the map.
     *
     * @param name the name of the member
     * @param value the value of the member (may be {@code null})
     * @return this builder
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public @NonNull Builder value(final @NonNull String name, final @Nullable Serializable value)
        throws NullPointerException {
      this.values.put(Objects.requireNonNull(name, "name must not be null"), value);
      return this;
    }

    /**
     * Adds a member to the map, using the {@link AuditValue}'s name as the key and its value as the value.
     *
     * @param value the audit value to add
     * @return this builder
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public @NonNull Builder value(final @NonNull AuditValue<? extends Serializable> value)
        throws NullPointerException {
      Objects.requireNonNull(value, "value must not be null");
      this.values.put(value.getName(), value.getValue());
      return this;
    }

  }

}
