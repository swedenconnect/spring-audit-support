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

import org.bson.Document;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The default {@link MongoAuditEventDao} implementation, backed by a
 * {@link org.springframework.data.mongodb.core.MongoOperations MongoOperations} (typically a {@code MongoTemplate}).
 * <p>
 * Each audit event is stored as a document. A set of fields ({@code eventTime}, {@code principal}, {@code eventType},
 * {@code applicationName}, {@code correlationId}) is used for querying, while the complete event is stored as JSON in
 * the {@code eventData} field using an {@link AuditEventMapper}. On read, the event is reconstructed from the
 * {@code eventData} field, so no information is lost. Timestamps are stored as UTC instants.
 * </p>
 * <p>
 * The collection name is configurable (defaulting to {@value #DEFAULT_COLLECTION_NAME}); the field names are fixed. See
 * {@code docs/mongo.md}. An application whose schema differs should implement {@link MongoAuditEventDao} directly
 * instead of using this class.
 * </p>
 *
 * @author Martin Lindström
 */
public class DefaultMongoAuditEventDao implements MongoAuditEventDao {

  /** The default collection name. */
  public static final String DEFAULT_COLLECTION_NAME = "audit_events";

  /** The {@code eventTime} field. */
  private static final String EVENT_TIME = "eventTime";

  /** The {@code principal} field. */
  private static final String PRINCIPAL = "principal";

  /** The {@code eventType} field. */
  private static final String EVENT_TYPE = "eventType";

  /** The {@code applicationName} field. */
  private static final String APPLICATION_NAME = "applicationName";

  /** The {@code correlationId} field. */
  private static final String CORRELATION_ID = "correlationId";

  /** The {@code eventData} field (the full event as JSON). */
  private static final String EVENT_DATA = "eventData";

  /** The Mongo operations. */
  private final MongoOperations mongoOperations;

  /** The mapper used to (de)serialize the event to/from the {@code eventData} field. */
  private final AuditEventMapper eventMapper;

  /** The collection name. */
  private final String collectionName;

  /**
   * Constructor using the {@link #DEFAULT_COLLECTION_NAME default collection name}.
   *
   * @param mongoOperations the Mongo operations
   * @param eventMapper the mapper used to (de)serialize the event
   */
  public DefaultMongoAuditEventDao(final @NonNull MongoOperations mongoOperations,
      final @NonNull AuditEventMapper eventMapper) {
    this(mongoOperations, eventMapper, DEFAULT_COLLECTION_NAME);
  }

  /**
   * Constructor.
   *
   * @param mongoOperations the Mongo operations
   * @param eventMapper the mapper used to (de)serialize the event
   * @param collectionName the collection holding the audit events (if {@code null} or blank, the
   *     {@link #DEFAULT_COLLECTION_NAME default} is used)
   */
  public DefaultMongoAuditEventDao(final @NonNull MongoOperations mongoOperations,
      final @NonNull AuditEventMapper eventMapper, final @Nullable String collectionName) {
    this.mongoOperations = Objects.requireNonNull(mongoOperations, "mongoOperations must not be null");
    this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");
    this.collectionName =
        Optional.ofNullable(collectionName).filter(StringUtils::hasText).orElse(DEFAULT_COLLECTION_NAME);
  }

  /** {@inheritDoc} */
  @Override
  public void save(final @NonNull AuditEvent event) {
    final se.swedenconnect.spring.audit.AuditEvent structured =
        event instanceof final se.swedenconnect.spring.audit.AuditEvent e ? e : null;
    final String applicationName = structured != null && structured.getApplicationName() != null
        ? structured.getApplicationName().getName() : null;
    final String correlationId = structured != null && structured.getCorrelationId() != null
        ? structured.getCorrelationId().getValue() : null;

    final Document document = new Document()
        .append(EVENT_TIME, Date.from(event.getTimestamp()))
        .append(PRINCIPAL, event.getPrincipal())
        .append(EVENT_TYPE, event.getType())
        .append(APPLICATION_NAME, applicationName)
        .append(CORRELATION_ID, correlationId)
        .append(EVENT_DATA, this.eventMapper.write(event));
    this.mongoOperations.insert(document, this.collectionName);
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
    final List<Criteria> criteria = new ArrayList<>();
    if (principal != null) {
      criteria.add(Criteria.where(PRINCIPAL).is(principal));
    }
    if (after != null) {
      criteria.add(Criteria.where(EVENT_TIME).gt(Date.from(after)));
    }
    if (type != null) {
      criteria.add(Criteria.where(EVENT_TYPE).is(type));
    }
    final Query query = new Query();
    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }
    query.with(Sort.by(Sort.Direction.DESC, EVENT_TIME));
    return this.read(query);
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull List<AuditEvent> findRecent(final int limit) {
    final Query query = new Query().with(Sort.by(Sort.Direction.DESC, EVENT_TIME));
    if (limit > 0) {
      query.limit(limit);
    }
    return this.read(query);
  }

  /**
   * Executes the query and reconstructs the audit events from the {@code eventData} field.
   *
   * @param query the query
   * @return the matching audit events
   */
  private List<AuditEvent> read(final Query query) {
    return this.mongoOperations.find(query, Document.class, this.collectionName).stream()
        .map(document -> this.eventMapper.read(document.getString(EVENT_DATA)))
        .toList();
  }

}
