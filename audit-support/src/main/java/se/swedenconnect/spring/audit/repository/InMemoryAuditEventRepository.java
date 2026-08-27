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
package se.swedenconnect.spring.audit.repository;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * An in-memory {@link ExtendedAuditEventRepository} that delegates storage to Spring's
 * {@link org.springframework.boot.actuate.audit.InMemoryAuditEventRepository}, adding the event
 * {@link AbstractAuditEventRepository filtering} and {@link ExtendedAuditEventRepository extended find} capabilities of
 * this library.
 * <p>
 * Events are held in a bounded, in-memory buffer &ndash; once the configured capacity is reached, the oldest events are
 * discarded. As the events are not persisted, this repository is primarily intended for testing, development, or
 * single-instance setups rather than durable audit storage. It always {@link #supportsFind() supports find}.
 * </p>
 *
 * @author Martin Lindström
 */
public class InMemoryAuditEventRepository extends AbstractAuditEventRepository {

  /** Spring's repository. */
  private final org.springframework.boot.actuate.audit.InMemoryAuditEventRepository delegate;

  /**
   * Constructor for setting up the repository for no filtering and a default capacity (see
   * {@link org.springframework.boot.actuate.audit.InMemoryAuditEventRepository#setCapacity(int)}).
   */
  public InMemoryAuditEventRepository() {
    this(Integer.MIN_VALUE, null);
  }

  /**
   * Constructor for setting up the repository for no filtering with a given capacity.
   *
   * @param capacity the number of events to keep in memory
   * @throws IllegalArgumentException if {@code capacity} is 0
   */
  public InMemoryAuditEventRepository(final int capacity) {
    this(capacity, null);
  }

  /**
   * Constructor for setting up the repository with the given filter and a default capacity (see
   * {@link org.springframework.boot.actuate.audit.InMemoryAuditEventRepository#setCapacity(int)}).
   *
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public InMemoryAuditEventRepository(final @Nullable Predicate<AuditEvent> filter) {
    this(Integer.MIN_VALUE, filter);
  }

  /**
   * Constructor for setting up the repository with the given capacity and filter.
   *
   * @param capacity the number of events to keep in memory
   * @param filter the filter (if {@code null}, no filtering is performed)
   * @throws IllegalArgumentException if {@code capacity} is 0
   */
  public InMemoryAuditEventRepository(final int capacity, final @Nullable Predicate<AuditEvent> filter) {
    super(filter);
    if (capacity == 0) {
      throw new IllegalArgumentException("capacity must be greater than 0");
    }
    this.delegate = capacity == Integer.MIN_VALUE
        ? new org.springframework.boot.actuate.audit.InMemoryAuditEventRepository()
        : new org.springframework.boot.actuate.audit.InMemoryAuditEventRepository(capacity);
  }

  /**
   * Set the capacity of this event repository.
   *
   * @param capacity the capacity
   */
  public void setCapacity(final int capacity) {
    this.delegate.setCapacity(capacity);
  }

  /**
   * Stores the event by delegating to the underlying Spring
   * {@link org.springframework.boot.actuate.audit.InMemoryAuditEventRepository}.
   *
   * @param event the audit event to store
   */
  @Override
  protected void addEvent(final @NonNull AuditEvent event) {
    this.delegate.add(event);
  }

  /** {@inheritDoc} */
  @Override
  protected @NonNull Iterator<AuditEvent> getEvents() {
    // Spring's InMemoryAuditEventRepository returns events oldest-first, but the getEvents() contract requires the
    // most recent event first, so the list is reversed.
    return this.delegate.find(null, null, null).reversed().iterator();
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code true}; an in-memory repository holds the events and can serve queries
   */
  @Override
  public boolean supportsFind() {
    return true;
  }
}
