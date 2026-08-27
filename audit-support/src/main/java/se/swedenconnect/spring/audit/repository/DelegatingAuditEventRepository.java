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
import org.springframework.boot.actuate.audit.AuditEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A composite {@link ExtendedAuditEventRepository} that delegates to a list of underlying {@link AuditEventRepository}
 * instances.
 * <p>
 * {@link #add(AuditEvent)} first applies this repository's own {@link Predicate filter} and then forwards accepted
 * events to <b>all</b> delegates. {@link #find(String, Instant, String) find} tries each delegate in order and returns
 * the first non-empty result.
 * </p>
 * <p>
 * <b>Filtering.</b> When a delegating repository is used, the event filter should normally be configured <em>here</em>
 * (on the delegating repository) and not on the individual delegates, so that filtering is applied once, consistently,
 * for all of them.
 * </p>
 * <p>
 * <b>Write failure handling.</b> Every delegate is attempted even if an earlier one fails, and each failure is logged.
 * Whether a failure is propagated as an {@link AuditEventWriteException} (after all delegates have been attempted) is
 * resolved per delegate: a delegate that has explicitly set its
 * {@link AbstractAuditEventRepository#setThrowOnWriteFail(boolean) throw-on-write-failure} flag keeps that setting,
 * while a delegate that has left it unset (or that is not an {@link AbstractAuditEventRepository}) inherits the value
 * configured on this delegating repository via {@link #setThrowOnWriteFail(boolean)}. If the resolved value of any
 * attempted delegate is "throw", an {@link AuditEventWriteException} is thrown once all delegates have been attempted.
 * </p>
 *
 * @author Martin Lindström
 */
public class DelegatingAuditEventRepository implements ExtendedAuditEventRepository {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(DelegatingAuditEventRepository.class);

  /** The underlying repositories. */
  private final List<AuditEventRepository> repositories;

  /** The filter. */
  private final Predicate<AuditEvent> filter;

  /** Supplies the write-failure behavior for delegates that have not set their own. */
  private Boolean throwOnWriteFail;

  /**
   * Constructor setting up the repository with no filtering.
   *
   * @param repositories the underlying repositories
   */
  public DelegatingAuditEventRepository(final @NonNull List<AuditEventRepository> repositories) {
    this(repositories, null);
  }

  /**
   * Constructor.
   *
   * @param repositories the underlying repositories
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public DelegatingAuditEventRepository(final @NonNull List<AuditEventRepository> repositories,
      final @Nullable Predicate<AuditEvent> filter) {
    this.repositories = Objects.requireNonNull(repositories, "repositories must not be null");
    this.filter = Optional.ofNullable(filter).orElseGet(() -> event -> true);
  }

  /**
   * Assigns the write-failure behavior used for delegates that have not explicitly configured their own
   * {@code throwOnWriteFail}. If not set, the behavior defaults to throwing.
   *
   * @param throwOnWriteFail {@code true} to throw an {@link AuditEventWriteException} on write failure, {@code false}
   *     to only log it
   */
  public void setThrowOnWriteFail(final boolean throwOnWriteFail) {
    this.throwOnWriteFail = throwOnWriteFail;
  }

  /**
   * Adds the event to all delegate repositories that pass this repository's filter.
   *
   * @param event the audit event to add
   * @throws AuditEventWriteException if a delegate fails and its resolved write-failure policy is to throw
   */
  @Override
  public void add(final @NonNull AuditEvent event) throws AuditEventWriteException {
    if (event == null) {
      return;
    }
    if (!this.filter.test(event)) {
      log.trace("Audit event {} not logged - filter rules excludes it", event.getType());
      return;
    }
    AuditEventWriteException pending = null;
    for (final AuditEventRepository repository : this.repositories) {
      try {
        repository.add(event);
      }
      catch (final Exception e) {
        log.error("Failed to add audit event '{}' to {}", event.getType(), repository.getClass().getSimpleName(), e);
        if (this.shouldPropagate(repository)) {
          if (pending == null) {
            pending = e instanceof final AuditEventWriteException awe
                ? awe
                : new AuditEventWriteException(
                    "Failed to write audit event of type '%s'".formatted(event.getType()), e);
          }
          else {
            pending.addSuppressed(e);
          }
        }
      }
    }
    if (pending != null) {
      throw pending;
    }
  }

  /**
   * Resolves whether a failure from the given delegate should be propagated: an explicit setting on the delegate wins,
   * otherwise this repository's value is used (unset behaves as "throw").
   *
   * @param repository the delegate that failed
   * @return {@code true} if the failure should be propagated
   */
  private boolean shouldPropagate(final AuditEventRepository repository) {
    final Boolean explicit = repository instanceof final AbstractAuditEventRepository aer
        ? aer.getThrowOnWriteFail()
        : null;
    final Boolean effective = explicit != null ? explicit : this.throwOnWriteFail;
    return !Boolean.FALSE.equals(effective);
  }

  /**
   * Tries each delegate in order, returning the first non-empty result.
   */
  @Override
  public @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
    for (final AuditEventRepository repository : this.repositories) {
      final List<AuditEvent> events = repository.find(principal, after, type);
      if (events != null && !events.isEmpty()) {
        return events;
      }
    }
    return List.of();
  }

  /**
   * Tries each delegate that is an {@link ExtendedAuditEventRepository} in order, returning the first non-empty result.
   */
  @Override
  public @NonNull List<AuditEvent> find(final @NonNull Predicate<AuditEvent> criteria) {
    for (final AuditEventRepository repository : this.repositories) {
      if (repository instanceof final ExtendedAuditEventRepository extended) {
        final List<AuditEvent> events = extended.find(criteria);
        if (events != null && !events.isEmpty()) {
          return events;
        }
      }
    }
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code true} if at least one delegate can serve queries
   */
  @Override
  public boolean supportsFind() {
    return this.repositories.stream()
        .anyMatch(r -> !(r instanceof final ExtendedAuditEventRepository extended) || extended.supportsFind());
  }

}
