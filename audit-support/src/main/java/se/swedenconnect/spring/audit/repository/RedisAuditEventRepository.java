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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An {@link ExtendedAuditEventRepository} that stores audit events in a Redis <b>sorted set</b> using
 * <a href="https://spring.io/projects/spring-data-redis">Spring Data Redis</a>.
 * <p>
 * Each event is added as a member of the sorted set with its timestamp (epoch milliseconds) as the score. This gives
 * time-ordered storage and lets {@link #find(String, Instant, String)} use an efficient
 * {@link ZSetOperations#reverseRangeByScore(Object, double, double) range-by-score} query for the {@code after}
 * criterion; the {@code principal} and {@code type} criteria are then applied in memory. Queries return events most
 * recent first.
 * </p>
 * <p>
 * The complete event is stored as JSON (via the {@link AuditEventMapper}) and reconstructed on read, so no information
 * is lost. As with any remote store there is no automatic capacity limit &ndash; see {@code docs/redis.md} for
 * retention strategies.
 * </p>
 *
 * @author Martin Lindström
 */
public class RedisAuditEventRepository extends AbstractAuditEventRepository {

  /** The default maximum number of events fetched for {@link #find(Predicate) predicate-based} queries. */
  public static final int DEFAULT_MAX_FETCH = 1000;

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(RedisAuditEventRepository.class);

  /** The Redis sorted set operations. */
  private final ZSetOperations<String, String> zSetOps;

  /** The name of the Redis key holding the audit event sorted set. */
  private final String keyName;

  /** The audit event mapper. */
  private final AuditEventMapper eventMapper;

  /** The maximum number of events fetched for predicate-based queries. */
  private int maxFetch = DEFAULT_MAX_FETCH;

  /**
   * Constructor setting up the repository with no filtering.
   *
   * @param redisTemplate the Redis template
   * @param keyName the name of the Redis key holding the audit event sorted set
   * @param mapper the mapper for creating/reading JSON events
   */
  public RedisAuditEventRepository(final @NonNull StringRedisTemplate redisTemplate, final @NonNull String keyName,
      final @NonNull AuditEventMapper mapper) {
    this(redisTemplate, keyName, mapper, null);
  }

  /**
   * Constructor.
   *
   * @param redisTemplate the Redis template
   * @param keyName the name of the Redis key holding the audit event sorted set
   * @param mapper the mapper for creating/reading JSON events
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public RedisAuditEventRepository(final @NonNull StringRedisTemplate redisTemplate, final @NonNull String keyName,
      final @NonNull AuditEventMapper mapper, final @Nullable Predicate<AuditEvent> filter) {
    super(filter);
    this.zSetOps = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null").opsForZSet();
    this.keyName = Objects.requireNonNull(keyName, "keyName must not be null");
    this.eventMapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  /**
   * Assigns the maximum number of events fetched when serving a {@link #find(Predicate) predicate-based} query. Since
   * such queries are evaluated in memory, this bounds the number of events loaded from Redis.
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
    log.debug("Audit logging event '{}' for principal '{}' ...", event.getType(), event.getPrincipal());
    this.zSetOps.add(this.keyName, this.eventMapper.write(event), event.getTimestamp().toEpochMilli());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The {@code after} criterion is pushed down to Redis as a range-by-score query; {@code principal} and {@code type}
   * are then matched in memory.
   * </p>
   */
  @Override
  public @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
    final double min = after != null ? after.toEpochMilli() : Double.NEGATIVE_INFINITY;
    final Set<String> events = this.zSetOps.reverseRangeByScore(this.keyName, min, Double.POSITIVE_INFINITY);
    return Optional.ofNullable(events).orElseGet(Set::of).stream()
        .map(this.eventMapper::read)
        .filter(e -> type == null || type.equals(e.getType()))
        .filter(e -> principal == null || principal.equals(e.getPrincipal()))
        .filter(e -> after == null || after.isBefore(e.getTimestamp()))
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  protected @NonNull Iterator<AuditEvent> getEvents() {
    final Set<String> events = this.zSetOps.reverseRange(this.keyName, 0, this.maxFetch - 1);
    final List<AuditEvent> result = Optional.ofNullable(events).orElseGet(Set::of).stream()
        .map(this.eventMapper::read)
        .toList();
    if (result.size() >= this.maxFetch) {
      log.warn("Predicate-based find is limited to the {} most recent audit events; "
          + "older events are not considered", this.maxFetch);
    }
    return result.iterator();
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code true}; Redis holds the events and can serve queries
   */
  @Override
  public boolean supportsFind() {
    return true;
  }

}
