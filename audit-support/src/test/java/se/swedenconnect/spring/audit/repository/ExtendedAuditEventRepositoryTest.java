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
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.AuditType;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for the predicate factories and the default {@code find} method of
 * {@link ExtendedAuditEventRepository}.
 *
 * @author Martin Lindström
 */
class ExtendedAuditEventRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

  private static se.swedenconnect.spring.audit.AuditEvent event() {
    return AuditEventBuilder.builder()
        .type("login")
        .timestamp(NOW)
        .principal("alice")
        .applicationName(new ApplicationName("test-app"))
        .correlationId(CorrelationID.of("abc-123"))
        .build();
  }

  @Test
  void testPrincipalPredicate() {
    assertThat(ExtendedAuditEventRepository.principal("alice")).accepts(event());
    assertThat(ExtendedAuditEventRepository.principal("bob")).rejects(event());
  }

  @Test
  void testIsAfterPredicate() {
    assertThat(ExtendedAuditEventRepository.isAfter(NOW.minusSeconds(1))).accepts(event());
    assertThat(ExtendedAuditEventRepository.isAfter(NOW.plusSeconds(1))).rejects(event());
  }

  @Test
  void testTypePredicate() {
    assertThat(ExtendedAuditEventRepository.type("login")).accepts(event());
    assertThat(ExtendedAuditEventRepository.type(AuditType.of("login"))).accepts(event());
    assertThat(ExtendedAuditEventRepository.type(AuditType.of("logout"))).rejects(event());
  }

  @Test
  void testApplicationNamePredicate() {
    assertThat(ExtendedAuditEventRepository.applicationName("test-app")).accepts(event());
    assertThat(ExtendedAuditEventRepository.applicationName(new ApplicationName("test-app"))).accepts(event());
    assertThat(ExtendedAuditEventRepository.applicationName("other-app")).rejects(event());
  }

  @Test
  void testApplicationNamePredicateRejectsPlainAuditEvents() {
    assertThat(ExtendedAuditEventRepository.applicationName("test-app"))
        .rejects(new AuditEvent("alice", "login", Map.of()));
  }

  @Test
  void testCorrelationIdPredicate() {
    assertThat(ExtendedAuditEventRepository.correlationId("abc-123")).accepts(event());
    assertThat(ExtendedAuditEventRepository.correlationId(CorrelationID.of("abc-123"))).accepts(event());
    assertThat(ExtendedAuditEventRepository.correlationId("other")).rejects(event());
  }

  @Test
  void testCorrelationIdPredicateRejectsPlainAuditEvents() {
    assertThat(ExtendedAuditEventRepository.correlationId("abc-123"))
        .rejects(new AuditEvent("alice", "login", Map.of()));
  }

  @Test
  void testDefaultFindWithoutCriteria() {
    final TestRepository repository = new TestRepository(true);
    repository.add(event());

    assertThat(repository.find(null, null, null)).hasSize(1);
  }

  @Test
  void testDefaultFindWithAllCriteria() {
    final TestRepository repository = new TestRepository(true);
    repository.add(event());

    assertThat(repository.find("alice", NOW.minusSeconds(1), "login")).hasSize(1);
    assertThat(repository.find("bob", NOW.minusSeconds(1), "login")).isEmpty();
    assertThat(repository.find("alice", NOW.plusSeconds(1), "login")).isEmpty();
    assertThat(repository.find("alice", NOW.minusSeconds(1), "logout")).isEmpty();
  }

  @Test
  void testDefaultFindWhenFindIsNotSupported() {
    final TestRepository repository = new TestRepository(false);
    repository.add(event());

    assertThat(repository.find(null, null, null)).isEmpty();
  }

  /**
   * A minimal {@link ExtendedAuditEventRepository} holding its events in a list.
   */
  private static class TestRepository implements ExtendedAuditEventRepository {

    private final List<AuditEvent> events = new ArrayList<>();

    private final boolean supportsFind;

    TestRepository(final boolean supportsFind) {
      this.supportsFind = supportsFind;
    }

    @Override
    public void add(final @NonNull AuditEvent event) {
      this.events.add(event);
    }

    @Override
    public boolean supportsFind() {
      return this.supportsFind;
    }

    @Override
    public @NonNull List<AuditEvent> find(final @NonNull Predicate<AuditEvent> criteria) {
      return this.events.stream().filter(criteria).toList();
    }
  }

}
