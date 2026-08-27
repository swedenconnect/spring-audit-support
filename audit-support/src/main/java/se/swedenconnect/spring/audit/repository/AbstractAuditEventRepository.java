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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An abstract base class for {@link ExtendedAuditEventRepository} implementations that adds <em>event filtering</em> on
 * top of the storage supplied by subclasses.
 * <p>
 * A repository is created with an optional {@link Predicate filter}. Every event passed to {@link #add(AuditEvent)} is
 * tested against this filter, and only events that are accepted are forwarded to {@link #addEvent(AuditEvent)} for
 * storage; rejected events are silently discarded (logged at {@code TRACE} level). If no filter is supplied, all events
 * are accepted. This lets an application control which audit events it actually persists &ndash; typically based on the
 * event type. The static {@link #inclusionPredicate(List) inclusion}, {@link #exclusionPredicate(List) exclusion} and
 * {@link #inclusionExclusionPredicate(List, List) inclusion/exclusion} factories build such type-based filters for use
 * with the constructor.
 * </p>
 * <p>
 * Note that this filter is a <em>write-side</em> concern and is distinct from the query predicate passed to
 * {@link #find(Predicate)}. Querying is implemented by iterating the events returned by {@link #getEvents()} and
 * returning those that match the query predicate. Subclasses provide the actual storage by implementing
 * {@link #addEvent(AuditEvent)} and, if querying is supported, {@link #getEvents()} and {@link #supportsFind()}.
 * </p>
 * <p>
 * <b>Write failure handling.</b> If {@link #addEvent(AuditEvent)} fails, the failure is always logged at {@code ERROR}.
 * In addition, an {@link AuditEventWriteException} is thrown by default, so that a failure to audit is not silently
 * lost. Setting {@link #setThrowOnWriteFail(boolean)} to {@code false} suppresses the exception (the failure is then
 * only logged), so that a failing audit sink does not break the operation that triggered the event. If the flag is
 * left unset it behaves as {@code true}, unless this repository is used as a subordinate of a
 * {@link DelegatingAuditEventRepository}, in which case the unset state lets the delegating repository supply the
 * value.
 * </p>
 *
 * @author Martin Lindström
 */
public abstract class AbstractAuditEventRepository implements ExtendedAuditEventRepository {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(AbstractAuditEventRepository.class);

  /** The filter. */
  private final Predicate<AuditEvent> filter;

  /**
   * Whether a write failure should be thrown or only logged. {@code null} means "unset", which behaves as {@code true}
   * for a standalone repository, but lets a {@link DelegatingAuditEventRepository} supply the value.
   */
  private Boolean throwOnWriteFail;

  /**
   * Constructor setting up a filter that accepts all events.
   */
  public AbstractAuditEventRepository() {
    this(null);
  }

  /**
   * Constructor.
   *
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public AbstractAuditEventRepository(final @Nullable Predicate<AuditEvent> filter) {
    this.filter = Optional.ofNullable(filter).orElseGet(() -> e -> true);
  }

  /**
   * Assigns whether a failure to write an audit event should, in addition to always being logged at {@code ERROR},
   * result in an {@link AuditEventWriteException} being thrown. If not set, the behavior defaults to throwing, unless
   * this repository is a subordinate of a {@link DelegatingAuditEventRepository}, in which case the unset state lets
   * the delegating repository supply the value.
   *
   * @param throwOnWriteFail {@code true} to also throw on write failure, {@code false} to only log it
   */
  public void setThrowOnWriteFail(final boolean throwOnWriteFail) {
    this.throwOnWriteFail = throwOnWriteFail;
  }

  /**
   * Gets the configured write-failure behavior, or {@code null} if it has not been explicitly set. Used by
   * {@link DelegatingAuditEventRepository} to determine whether this (subordinate) repository has an explicit setting
   * or should inherit the delegating repository's value.
   *
   * @return {@code true}/{@code false} if explicitly set, or {@code null} if unset
   */
  @Nullable Boolean getThrowOnWriteFail() {
    return this.throwOnWriteFail;
  }

  /**
   * Adds an audit event, provided it is accepted by the configured filter. Events rejected by the filter are silently
   * discarded (logged at {@code TRACE} level). Accepted events are passed to {@link #addEvent(AuditEvent)}.
   * <p>
   * If storing the event fails, the failure is logged at {@code ERROR} and, unless
   * {@link #setThrowOnWriteFail(boolean) throwOnWriteFail} is {@code false}, an {@link AuditEventWriteException} is
   * (re)thrown.
   * </p>
   *
   * @param event the audit event to add
   * @throws AuditEventWriteException if storing the event fails and {@code throwOnWriteFail} is {@code true}
   */
  @Override
  public final void add(final @NonNull AuditEvent event) throws AuditEventWriteException {
    if (event != null) {
      if (this.filter.test(event)) {
        try {
          this.addEvent(event);
        }
        catch (final Exception e) {
          final AuditEventWriteException writeException = e instanceof final AuditEventWriteException awe
              ? awe
              : new AuditEventWriteException(
                  "Failed to write audit event of type '%s'".formatted(event.getType()), e);
          // Always log the failure - the exception may be swallowed further up the audit event publication chain.
          log.error("Failed to write audit event '{}'", event.getType(), writeException);
          // Unset (null) behaves as "throw"; only an explicit false suppresses the exception.
          if (!Boolean.FALSE.equals(this.throwOnWriteFail)) {
            throw writeException;
          }
        }
      }
      else {
        log.trace("Audit event {} not logged - filter rules excludes it", event.getType());
      }
    }
  }

  /**
   * Stores an audit event that has passed the filter. Implemented by subclasses to perform the actual persistence or
   * forwarding.
   * <p>
   * Implementations should let failures propagate as (unchecked) exceptions; {@link #add(AuditEvent)} applies the
   * configured {@link #setThrowOnWriteFail(boolean) write-failure policy}.
   * </p>
   *
   * @param event the audit event to store
   */
  protected abstract void addEvent(final @NonNull AuditEvent event);

  /**
   * {@inheritDoc}
   * <p>
   * The implementation iterates the events returned by {@link #getEvents()} and includes those that satisfy the
   * criteria. If the repository does not {@link #supportsFind() support find}, an empty list is returned.
   * </p>
   */
  @Override
  public @NonNull List<AuditEvent> find(final @NonNull Predicate<AuditEvent> criteria) {
    if (!this.supportsFind()) {
      return Collections.emptyList();
    }
    final List<AuditEvent> events = new ArrayList<>();
    final Iterator<AuditEvent> iterator = this.getEvents();
    while (iterator.hasNext()) {
      final AuditEvent event = iterator.next();
      if (criteria.test(event)) {
        events.add(event);
      }
    }
    return events;
  }

  /**
   * Returns an iterator for the events held by the repository where the most recent event is returned first.
   *
   * @return an {@link Iterator} to the events held by the repository
   * @throws UnsupportedOperationException is thrown if the implementation does not support find, i.e.,
   *     {@link #supportsFind()} returns {@code false}
   */
  protected abstract @NonNull Iterator<AuditEvent> getEvents() throws UnsupportedOperationException;

  /**
   * Returns an audit event filter that accepts a list of event types that are accepted, i.e., a literal whitelist.
   * <p>
   * If the {@code types} parameter is an empty list, no events are accepted &ndash; an empty whitelist accepts nothing.
   * This is deliberately different from {@link #inclusionExclusionPredicate(List, List)}, where an empty list of
   * included types means "no inclusion constraint". Use this method when the caller assembles the list of accepted
   * types itself, so that an empty list fails closed, and the other method when the lists come from configuration,
   * where an unassigned setting means "no constraint".
   * </p>
   *
   * @param types the types that are accepted
   * @return a {@link Predicate} that returns {@code true} if an event should be audited
   */
  public static @NonNull Predicate<AuditEvent> inclusionPredicate(final @NonNull List<String> types) {
    return event -> types.contains(event.getType());
  }

  /**
   * Returns an audit event filter that excludes the given event types from being audited.
   * <p>
   * If the {@code types} parameter is an empty list, no events are excluded.
   * </p>
   *
   * @param types the types to exclude
   * @return a {@link Predicate} that returns {@code true} if an event should be audited
   */
  public static @NonNull Predicate<AuditEvent> exclusionPredicate(final @NonNull List<String> types) {
    return event -> !types.contains(event.getType());
  }

  /**
   * Returns an audit event filter that combines {@link #inclusionPredicate(List)} and
   * {@link #exclusionPredicate(List)}, intended for filters built from configuration where an unassigned setting means
   * "no constraint".
   * <p>
   * An empty {@code includeTypes} list is therefore treated as "no inclusion constraint" &ndash; all events are
   * accepted except those explicitly excluded. Note that this differs from {@link #inclusionPredicate(List)}, where an
   * empty list is a literal, empty whitelist that accepts nothing.
   * </p>
   * <p>
   * An event whose type appears in both lists is <b>excluded</b> &ndash; exclusion takes precedence over inclusion.
   * </p>
   *
   * @param includeTypes the types to include (if empty, all events are accepted except those explicitly excluded)
   * @param dontIncludeTypes the types to exclude (if empty, no events are excluded)
   * @return a {@link Predicate} that returns {@code true} if an event should be audited
   */
  public static @NonNull Predicate<AuditEvent> inclusionExclusionPredicate(
      final @NonNull List<String> includeTypes, final @NonNull List<String> dontIncludeTypes) {
    return includeTypes.isEmpty()
        ? exclusionPredicate(dontIncludeTypes)
        : inclusionPredicate(includeTypes).and(exclusionPredicate(dontIncludeTypes));
  }

}
