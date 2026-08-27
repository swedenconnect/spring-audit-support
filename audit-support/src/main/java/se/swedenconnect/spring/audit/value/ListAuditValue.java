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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A specialization of an {@link AuditValue} that holds a {@link List} as its value, i.e., the audit value represents a
 * list/array of values.
 *
 * @author Martin Lindström
 */
public class ListAuditValue extends AuditValue<LinkedList<? extends Serializable>> {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Constructor accepting a {@link List} as the audit value.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public ListAuditValue(final @NonNull String name, final @Nullable List<? extends Serializable> value) {
    super(name, value != null ? new LinkedList<>(value) : null);
  }

  /**
   * Constructor accepting an array as the audit value.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public ListAuditValue(final @NonNull String name, final Serializable @Nullable [] value) {
    this(name, value != null ? Arrays.asList(value) : null);
  }

  /**
   * Creates a new {@link Builder} for creating a {@link ListAuditValue}.
   *
   * @return a new {@link Builder}
   */
  public static @NonNull Builder builder() {
    return new Builder();
  }

  /**
   * A builder for creating a {@link ListAuditValue}.
   *
   * @author Martin Lindström
   */
  public static class Builder
      extends AuditValue.AbstractBuilder<LinkedList<? extends Serializable>, Builder> {

    /** The elements of the list. */
    private final LinkedList<Serializable> values = new LinkedList<>();

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
     * Builds the {@link ListAuditValue} from the assigned properties.
     *
     * @return a {@link ListAuditValue}
     * @throws NullPointerException if no name has been assigned
     */
    @Override
    public @NonNull ListAuditValue build() throws NullPointerException {
      return new ListAuditValue(Objects.requireNonNull(this.name, "name must be assigned"), this.values);
    }

    /**
     * Assigns the elements of the list, replacing any previously added elements. This is the whole-list setter
     * inherited from {@link AuditValue.AbstractBuilder}; it is equivalent to {@link #values(List)}.
     *
     * @param value the elements (or {@code null})
     * @return this builder
     */
    @Override
    public @NonNull Builder value(final @Nullable LinkedList<? extends Serializable> value) {
      return this.values(value);
    }

    /**
     * Assigns the elements of the list, replacing any previously added elements.
     *
     * @param values the elements (or {@code null})
     * @return this builder
     */
    public @NonNull Builder values(final @Nullable List<? extends Serializable> values) {
      this.values.clear();
      if (values != null) {
        this.values.addAll(values);
      }
      return this;
    }

    /**
     * Adds one or more elements to the list.
     * <p>
     * This is declared as a varargs method (rather than a single-element {@code value(Serializable)}) so that its
     * erasure does not clash with the inherited whole-list setter {@link #value(LinkedList)}. Passing a
     * {@link LinkedList} argument binds to that more specific whole-list overload and <em>replaces</em> the list; any
     * other argument is added as an element.
     * </p>
     *
     * @param values the elements to add
     * @return this builder
     * @throws NullPointerException if {@code values} or any element is {@code null}
     */
    public @NonNull Builder value(final @NonNull Serializable... values) throws NullPointerException {
      Objects.requireNonNull(values, "values must not be null");
      for (final Serializable value : values) {
        this.values.add(Objects.requireNonNull(value, "value must not be null"));
      }
      return this;
    }

  }

}
