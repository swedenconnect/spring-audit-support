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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link AbstractAuditEventRepository}, i.e., for the filtering, the write-failure policy and the
 * predicate-based find that the base class adds on top of the storage supplied by a subclass.
 *
 * @author Martin Lindström
 */
class AbstractAuditEventRepositoryTest {

  private static AuditEvent event(final String type) {
    return new AuditEvent(Instant.parse("2026-01-01T10:00:00Z"), "alice", type, Map.of());
  }

  @Test
  void testNoFilterAcceptsAllEvents() {
    final TestRepository repository = new TestRepository();

    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.stored).extracting(AuditEvent::getType).containsExactly("login", "logout");
  }

  @Test
  void testFilterRejectedEventsAreDiscarded() {
    final TestRepository repository = new TestRepository(ExtendedAuditEventRepository.type("login"));

    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.stored).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testNullEventIsIgnored() {
    final TestRepository repository = new TestRepository();

    assertThatCode(() -> repository.add(null)).doesNotThrowAnyException();
    assertThat(repository.stored).isEmpty();
  }

  @Test
  void testWriteFailureThrowsByDefault() {
    final TestRepository repository = new TestRepository();
    repository.failOnAdd = true;

    assertThatThrownBy(() -> repository.add(event("login")))
        .isInstanceOf(AuditEventWriteException.class)
        .hasMessageContaining("login");
  }

  @Test
  void testWriteFailureIsOnlyLoggedWhenConfigured() {
    final TestRepository repository = new TestRepository();
    repository.failOnAdd = true;
    repository.setThrowOnWriteFail(false);

    assertThatCode(() -> repository.add(event("login"))).doesNotThrowAnyException();
  }

  @Test
  void testAuditEventWriteExceptionIsNotWrapped() {
    final AuditEventWriteException thrown = new AuditEventWriteException("boom");
    final TestRepository repository = new TestRepository() {

      @Override
      protected void addEvent(final @NonNull AuditEvent event) {
        throw thrown;
      }
    };

    assertThatThrownBy(() -> repository.add(event("login"))).isSameAs(thrown);
  }

  @Test
  void testThrowOnWriteFailIsUnsetUntilAssigned() {
    final TestRepository repository = new TestRepository();
    assertThat(repository.getThrowOnWriteFail()).isNull();

    repository.setThrowOnWriteFail(true);
    assertThat(repository.getThrowOnWriteFail()).isTrue();

    repository.setThrowOnWriteFail(false);
    assertThat(repository.getThrowOnWriteFail()).isFalse();
  }

  @Test
  void testFindAppliesTheCriteria() {
    final TestRepository repository = new TestRepository();
    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.find(ExtendedAuditEventRepository.type("login")))
        .extracting(AuditEvent::getType).containsExactly("login");
    assertThat(repository.find(e -> true)).hasSize(2);
    assertThat(repository.find(e -> false)).isEmpty();
  }

  @Test
  void testFindReturnsEmptyListWhenFindIsNotSupported() {
    final TestRepository repository = new TestRepository();
    repository.supportsFind = false;
    repository.add(event("login"));

    // The events are stored, but can not be queried - getEvents() is never reached.
    assertThat(repository.stored).hasSize(1);
    assertThat(repository.find(e -> true)).isEmpty();
    assertThat(repository.find(null, null, null)).isEmpty();
  }

  /**
   * A minimal {@link AbstractAuditEventRepository} keeping its events in a list.
   */
  private static class TestRepository extends AbstractAuditEventRepository {

    private final List<AuditEvent> stored = new ArrayList<>();

    private boolean supportsFind = true;

    private boolean failOnAdd = false;

    TestRepository() {
      super();
    }

    TestRepository(final Predicate<AuditEvent> filter) {
      super(filter);
    }

    @Override
    protected void addEvent(final @NonNull AuditEvent event) {
      if (this.failOnAdd) {
        throw new IllegalStateException("boom");
      }
      this.stored.add(event);
    }

    @Override
    public boolean supportsFind() {
      return this.supportsFind;
    }

    @Override
    protected @NonNull Iterator<AuditEvent> getEvents() {
      if (!this.supportsFind) {
        throw new UnsupportedOperationException("This repository does not support find");
      }
      return this.stored.iterator();
    }
  }

}
