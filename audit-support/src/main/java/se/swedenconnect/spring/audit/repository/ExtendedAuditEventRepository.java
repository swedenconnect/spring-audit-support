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
import org.springframework.boot.actuate.audit.AuditEventRepository;
import se.swedenconnect.spring.audit.AuditType;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An extension of Spring's {@link AuditEventRepository} that adds a more expressive query API based on
 * {@link Predicate predicates}, together with a set of static predicate factories for the most common query criteria.
 * <p>
 * Spring's {@link AuditEventRepository#find(String, Instant, String)} only allows filtering on principal, timestamp,
 * and type. This interface adds {@link #find(Predicate)}, which accepts an arbitrary predicate, along with predicate
 * factories for criteria such as {@link #applicationName(String) application name} and
 * {@link #correlationId(CorrelationID) correlation ID} that are specific to the
 * {@link se.swedenconnect.spring.audit.AuditEvent structured audit events} produced by this library. Predicates may be
 * combined using {@link Predicate#and(Predicate)} and {@link Predicate#or(Predicate)}.
 * </p>
 * <p>
 * Not every repository can serve queries &ndash; some only forward events to an external sink. Such repositories return
 * {@code false} from {@link #supportsFind()}, in which case the {@code find} methods return an empty list.
 * </p>
 *
 * @author Martin Lindström
 */
public interface ExtendedAuditEventRepository extends AuditEventRepository {

  /**
   * Tells whether this event repository supports the find methods, i.e., whether the repository has access to already
   * processed events or not.
   *
   * @return {@code true} if find is supported, or {@code false} otherwise
   */
  boolean supportsFind();

  /**
   * Finds all audit events matching the supplied criteria.
   *
   * @param criteria the predicate that an event must satisfy to be included in the result
   * @return a list of matching audit events, or an empty list if the repository does not
   *     {@link #supportsFind() support find}
   */
  @NonNull List<AuditEvent> find(final @NonNull Predicate<AuditEvent> criteria);

  /**
   * Finds all audit events matching the supplied criteria by combining the non-{@code null} arguments into a predicate
   * and delegating to {@link #find(Predicate)}. Arguments that are {@code null} are not used as criteria.
   *
   * @param principal the principal to match, or {@code null} to not filter on principal
   * @param after the instant that an event's timestamp must be after, or {@code null} to not filter on timestamp
   * @param type the audit type to match, or {@code null} to not filter on type
   * @return a list of matching audit events, or an empty list if the repository does not
   *     {@link #supportsFind() support find}
   */
  @Override
  default @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
    return this.supportsFind()
        ? this.find(
        Optional.ofNullable(principal).map(ExtendedAuditEventRepository::principal).orElseGet(() -> p -> true)
            .and(Optional.ofNullable(after).map(ExtendedAuditEventRepository::isAfter).orElseGet(() -> i -> true))
            .and(Optional.ofNullable(type).map(ExtendedAuditEventRepository::type).orElseGet(() -> t -> true)))
        : Collections.emptyList();
  }

  /**
   * Creates a predicate that matches audit events with the given principal.
   *
   * @param principal the principal to match
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> principal(final @NonNull String principal) {
    return event -> Objects.equals(event.getPrincipal(), principal);
  }

  /**
   * Creates a predicate that matches audit events whose timestamp is after the given instant.
   *
   * @param after the instant that an event's timestamp must be after
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> isAfter(final @NonNull Instant after) {
    return event -> event.getTimestamp().isAfter(after);
  }

  /**
   * Creates a predicate that matches audit events of the given type.
   *
   * @param type the audit type to match
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> type(final @NonNull AuditType type) {
    return type(type.type());
  }

  /**
   * Creates a predicate that matches audit events of the given type.
   *
   * @param type the audit type to match, in its string representation
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> type(final @NonNull String type) {
    return event -> Objects.equals(event.getType(), type);
  }

  /**
   * Creates a predicate that matches {@link se.swedenconnect.spring.audit.AuditEvent structured} audit events with the
   * given application name. Events that are not structured audit events never match.
   *
   * @param applicationName the application name to match
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> applicationName(final @NonNull ApplicationName applicationName) {
    return event -> {
      if (event instanceof final se.swedenconnect.spring.audit.AuditEvent extEvent) {
        return Objects.equals(extEvent.getApplicationName(), applicationName);
      }
      else {
        return false;
      }
    };
  }

  /**
   * Creates a predicate that matches {@link se.swedenconnect.spring.audit.AuditEvent structured} audit events with the
   * given application name. Events that are not structured audit events never match.
   *
   * @param applicationName the application name to match, in its string representation
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> applicationName(final @NonNull String applicationName) {
    return applicationName(new ApplicationName(applicationName));
  }

  /**
   * Creates a predicate that matches {@link se.swedenconnect.spring.audit.AuditEvent structured} audit events with the
   * given correlation ID. Events that are not structured audit events never match.
   *
   * @param correlationId the correlation ID to match
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> correlationId(final @NonNull CorrelationID correlationId) {
    return event -> {
      if (event instanceof final se.swedenconnect.spring.audit.AuditEvent extEvent) {
        return Objects.equals(extEvent.getCorrelationId(), correlationId);
      }
      else {
        return false;
      }
    };
  }

  /**
   * Creates a predicate that matches {@link se.swedenconnect.spring.audit.AuditEvent structured} audit events with the
   * given correlation ID. Events that are not structured audit events never match.
   *
   * @param correlationId the correlation ID to match, in its string representation
   * @return a {@link Predicate}
   */
  static @NonNull Predicate<AuditEvent> correlationId(final @NonNull String correlationId) {
    return correlationId(CorrelationID.of(correlationId));
  }

}
