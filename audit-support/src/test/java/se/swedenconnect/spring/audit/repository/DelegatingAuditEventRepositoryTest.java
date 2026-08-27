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
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link DelegatingAuditEventRepository}.
 *
 * @author Martin Lindström
 */
class DelegatingAuditEventRepositoryTest {

  @Test
  void testConstructorNullRepositories() {
    assertThatThrownBy(() -> new DelegatingAuditEventRepository(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("repositories must not be null");
  }

  @Test
  void testAddForwardsToAllDelegates() {
    final RecordingRepository r1 = new RecordingRepository();
    final RecordingRepository r2 = new RecordingRepository();
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(r1, r2));

    final AuditEvent event = event("login");
    repo.add(event);

    assertThat(r1.events).containsExactly(event);
    assertThat(r2.events).containsExactly(event);
  }

  @Test
  void testAddRespectsFilter() {
    final RecordingRepository r1 = new RecordingRepository();
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(r1),
        AbstractAuditEventRepository.inclusionPredicate(List.of("login")));

    repo.add(event("logout"));
    repo.add(event("login"));

    assertThat(r1.events).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testAllDelegatesAttemptedDespiteFailure() {
    final FailingRepository failing = new FailingRepository();
    final RecordingRepository recording = new RecordingRepository();
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(failing, recording));

    // The failing (unset) delegate throws, but the second delegate must still receive the event.
    assertThatThrownBy(() -> repo.add(event("login"))).isInstanceOf(AuditEventWriteException.class);
    assertThat(recording.events).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testFailingDelegateThrowsByDefault() {
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(new FailingRepository()));
    assertThatThrownBy(() -> repo.add(event("login"))).isInstanceOf(AuditEventWriteException.class);
  }

  @Test
  void testDelegatingNoThrowSwallowsUnsetDelegate() {
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(new FailingRepository()));
    repo.setThrowOnWriteFail(false);
    assertThatCode(() -> repo.add(event("login"))).doesNotThrowAnyException();
  }

  @Test
  void testExplicitDelegateThrowWinsOverDelegatingNoThrow() {
    final FailingRepository failing = new FailingRepository();
    failing.setThrowOnWriteFail(true);
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(failing));
    repo.setThrowOnWriteFail(false);

    assertThatThrownBy(() -> repo.add(event("login"))).isInstanceOf(AuditEventWriteException.class);
  }

  @Test
  void testExplicitDelegateNoThrowWinsOverDelegatingThrow() {
    final FailingRepository failing = new FailingRepository();
    failing.setThrowOnWriteFail(false);
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(failing));
    repo.setThrowOnWriteFail(true);

    assertThatCode(() -> repo.add(event("login"))).doesNotThrowAnyException();
  }

  @Test
  void testNullEventIsIgnored() {
    final RecordingRepository recording = new RecordingRepository();
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(recording));

    assertThatCode(() -> repo.add(null)).doesNotThrowAnyException();
    assertThat(recording.events).isEmpty();
  }

  @Test
  void testMultipleFailuresAreSuppressedIntoOneException() {
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(new FailingRepository(), new FailingRepository()));

    assertThatThrownBy(() -> repo.add(event("login")))
        .isInstanceOf(AuditEventWriteException.class)
        .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
  }

  @Test
  void testAuditEventWriteExceptionFromDelegateIsPropagatedAsIs() {
    final AuditEventWriteException thrown = new AuditEventWriteException("delegate failure");
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(new ThrowingRepository(thrown)));

    assertThatThrownBy(() -> repo.add(event("login"))).isSameAs(thrown);
  }

  @Test
  void testFindReturnsFirstNonEmpty() {
    final RecordingRepository empty = new RecordingRepository();
    final RecordingRepository hasData = new RecordingRepository();
    hasData.findResult = List.of(event("login"));
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(empty, hasData));

    assertThat(repo.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testFindReturnsEmptyWhenAllEmpty() {
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(new RecordingRepository(), new RecordingRepository()));
    assertThat(repo.find(null, null, null)).isEmpty();
  }

  @Test
  void testFindPredicateDelegatesToExtendedRepositories() {
    final InMemoryAuditEventRepository inMemory = new InMemoryAuditEventRepository();
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(inMemory));
    repo.add(event("login"));

    assertThat(repo.find(e -> "login".equals(e.getType())))
        .extracting(AuditEvent::getType)
        .containsExactly("login");
  }

  @Test
  void testSupportsFindRequiresAQueryableExtendedDelegate() {
    assertThat(new DelegatingAuditEventRepository(List.of(new InMemoryAuditEventRepository())).supportsFind())
        .isTrue();
    assertThat(new DelegatingAuditEventRepository(List.of(new WriteOnlyRepository())).supportsFind()).isFalse();
  }

  @Test
  void testOnlyPlainDelegatesDoNotSupportFind() {
    final RecordingRepository plain = new RecordingRepository();
    plain.findResult = List.of(event("login"));
    final DelegatingAuditEventRepository repo = new DelegatingAuditEventRepository(List.of(plain));

    // A plain AuditEventRepository can not serve a predicate-based query ...
    assertThat(repo.supportsFind()).isFalse();
    assertThat(repo.find(e -> true)).isEmpty();

    // ... but it is still used by the Spring Boot find method.
    assertThat(repo.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testWriteOnlyExtendedDelegateAndPlainDelegateDoNotSupportFind() {
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(new WriteOnlyRepository(), new RecordingRepository()));

    assertThat(repo.supportsFind()).isFalse();
    assertThat(repo.find(e -> true)).isEmpty();
  }

  @Test
  void testQueryableExtendedDelegateSupportsFind() {
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(new InMemoryAuditEventRepository()));
    repo.add(event("login"));

    assertThat(repo.supportsFind()).isTrue();
    assertThat(repo.find(e -> true)).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testQueryableDelegateAnswersWhenMixedWithAWriteOnlyOne() {
    final WriteOnlyRepository writeOnly = new WriteOnlyRepository();
    final DelegatingAuditEventRepository repo =
        new DelegatingAuditEventRepository(List.of(writeOnly, new InMemoryAuditEventRepository()));
    repo.add(event("login"));

    assertThat(repo.supportsFind()).isTrue();
    assertThat(writeOnly.events).extracting(AuditEvent::getType).containsExactly("login");
    assertThat(repo.find(e -> true)).extracting(AuditEvent::getType).containsExactly("login");
  }

  private static AuditEvent event(final String type) {
    return new AuditEvent("alice", type, Map.of());
  }

  /**
   * A plain {@link AuditEventRepository} that records what it is given.
   */
  private static class RecordingRepository implements AuditEventRepository {

    private final List<AuditEvent> events = new ArrayList<>();

    private List<AuditEvent> findResult = List.of();

    @Override
    public void add(final @NonNull AuditEvent event) {
      this.events.add(event);
    }

    @Override
    public @NonNull List<AuditEvent> find(
        final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
      return this.findResult;
    }
  }

  /**
   * An {@link AbstractAuditEventRepository} whose write always fails, so its {@code throwOnWriteFail} resolution can be
   * exercised.
   */
  private static class FailingRepository extends AbstractAuditEventRepository {

    @Override
    protected void addEvent(final @NonNull AuditEvent event) {
      throw new IllegalStateException("boom");
    }

    @Override
    public boolean supportsFind() {
      return false;
    }

    @Override
    protected @NonNull Iterator<AuditEvent> getEvents() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A repository that always fails with the supplied exception.
   */
  private static class ThrowingRepository extends AbstractAuditEventRepository {

    private final RuntimeException exception;

    ThrowingRepository(final RuntimeException exception) {
      this.exception = exception;
    }

    @Override
    protected void addEvent(final @NonNull AuditEvent event) {
      throw this.exception;
    }

    @Override
    public boolean supportsFind() {
      return false;
    }

    @Override
    protected java.util.@NonNull Iterator<AuditEvent> getEvents() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * An {@link AbstractAuditEventRepository} that accepts writes but can not serve queries - like the file and syslog
   * repositories.
   */
  private static class WriteOnlyRepository extends AbstractAuditEventRepository {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    protected void addEvent(final @NonNull AuditEvent event) {
      this.events.add(event);
    }

    @Override
    public boolean supportsFind() {
      return false;
    }

    @Override
    protected java.util.@NonNull Iterator<AuditEvent> getEvents() {
      throw new UnsupportedOperationException("This repository does not support find");
    }
  }

}
