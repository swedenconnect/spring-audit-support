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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * An {@link ExtendedAuditEventRepository} that persists audit events to a database via an {@link AuditEventDao}.
 * <p>
 * The repository holds no storage or schema knowledge of its own; all storage access is delegated to the
 * {@link AuditEventDao}. Ready-to-use implementations exist for relational databases
 * ({@link DefaultJdbcAuditEventDao}, see {@code docs/jdbc.md}) and MongoDB ({@link DefaultMongoAuditEventDao}, see
 * {@code docs/mongo.md}), but an application with a different schema may supply its own.
 * </p>
 * <p>
 * Two query paths exist:
 * </p>
 * <ul>
 *   <li>{@link #find(String, Instant, String)} is pushed down to the {@link AuditEventDao}, letting the store do the
 *       filtering.</li>
 *   <li>{@link #find(Predicate)} evaluates an arbitrary predicate, which cannot be pushed down. It is therefore
 *       applied in memory over the {@link #setMaxFetch(int) most recent} events; if that limit truncates the result, a
 *       warning is logged.</li>
 * </ul>
 *
 * @author Martin Lindström
 */
public class DatabaseAuditEventRepository extends AbstractAuditEventRepository {

  /** The default maximum number of events fetched for {@link #find(Predicate) predicate-based} queries. */
  public static final int DEFAULT_MAX_FETCH = 1000;

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(DatabaseAuditEventRepository.class);

  /** The DAO that performs the actual storage access. */
  private final AuditEventDao dao;

  /** The maximum number of events fetched for predicate-based queries. */
  private int maxFetch = DEFAULT_MAX_FETCH;

  /**
   * Constructor setting up the repository with no filtering.
   *
   * @param dao the DAO that performs the actual storage access
   */
  public DatabaseAuditEventRepository(final @NonNull AuditEventDao dao) {
    this(dao, null);
  }

  /**
   * Constructor.
   *
   * @param dao the DAO that performs the actual storage access
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public DatabaseAuditEventRepository(final @NonNull AuditEventDao dao, final @Nullable Predicate<AuditEvent> filter) {
    super(filter);
    this.dao = Objects.requireNonNull(dao, "dao must not be null");
  }

  /**
   * Assigns the maximum number of events fetched when serving a {@link #find(Predicate) predicate-based} query. Since
   * such queries are evaluated in memory, this bounds the number of events loaded from the store.
   *
   * @param maxFetch the maximum number of events to fetch (must be greater than 0)
   * @throws IllegalArgumentException if {@code maxFetch} is not greater than 0
   */
  public void setMaxFetch(final int maxFetch) throws IllegalArgumentException {
    if (maxFetch <= 0) {
      throw new IllegalArgumentException("maxFetch must be greater than 0");
    }
    this.maxFetch = maxFetch;
  }

  /** {@inheritDoc} */
  @Override
  protected void addEvent(final @NonNull AuditEvent event) {
    this.dao.save(event);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Delegates to {@link AuditEventDao#find(String, Instant, String)} so that the filtering is performed by the store.
   * </p>
   */
  @Override
  public @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
    return this.dao.find(principal, after, type);
  }

  /** {@inheritDoc} */
  @Override
  protected @NonNull Iterator<AuditEvent> getEvents() {
    final List<AuditEvent> events = this.dao.findRecent(this.maxFetch);
    if (events.size() >= this.maxFetch) {
      log.warn("Predicate-based find is limited to the {} most recent audit events; "
          + "older events are not considered", this.maxFetch);
    }
    return events.iterator();
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code true}; the database holds the events and can serve queries
   */
  @Override
  public boolean supportsFind() {
    return true;
  }

}
