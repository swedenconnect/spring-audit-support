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

import java.time.Instant;
import java.util.List;

/**
 * A storage-agnostic data access abstraction over the persistent storage of audit events.
 * <p>
 * This interface is the seam between {@link DatabaseAuditEventRepository} &ndash; which owns the generic behavior such
 * as filtering and predicate-based find &ndash; and the actual storage. The repository contains no storage or schema
 * knowledge; all of that lives behind an implementation of this interface. Ready-to-use implementations exist for
 * relational databases ({@link DefaultJdbcAuditEventDao}, see {@code docs/jdbc.md}) and MongoDB
 * ({@link DefaultMongoAuditEventDao}, see {@code docs/mongo.md}), and an application with a different schema may supply
 * its own.
 * </p>
 * <p>
 * The {@link JdbcAuditEventDao} and {@link MongoAuditEventDao} sub-interfaces are markers that let the
 * auto-configuration tell a JDBC and a MongoDB implementation apart; a custom implementation intended to be picked up
 * by the auto-configuration should implement the appropriate marker.
 * </p>
 *
 * @author Martin Lindström
 */
public interface AuditEventDao {

  /**
   * Persists a single audit event.
   *
   * @param event the audit event to store
   */
  void save(final @NonNull AuditEvent event);

  /**
   * Finds the stored audit events matching the supplied criteria, most recent first. Criteria that are {@code null} are
   * not applied.
   *
   * @param principal the principal to match, or {@code null} to not filter on principal
   * @param after the instant that an event's timestamp must be after, or {@code null} to not filter on timestamp
   * @param type the audit type to match, or {@code null} to not filter on type
   * @return a list of matching audit events, most recent first
   */
  @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type);

  /**
   * Returns the most recently stored audit events, most recent first, limited to at most {@code limit} events.
   * <p>
   * This is used to serve arbitrary {@link java.util.function.Predicate predicate}-based queries, which can not be
   * translated to the underlying store and are therefore evaluated in memory over a bounded window of recent events.
   * </p>
   *
   * @param limit the maximum number of events to return
   * @return a list of at most {@code limit} audit events, most recent first
   */
  @NonNull List<AuditEvent> findRecent(final int limit);

}
