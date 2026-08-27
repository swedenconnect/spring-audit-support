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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link DatabaseAuditEventRepository}, using a recording {@link AuditEventDao} so that the repository
 * behavior is verified independently of any database.
 *
 * @author Martin Lindström
 */
class DatabaseAuditEventRepositoryTest {

  private RecordingDao dao;

  private DatabaseAuditEventRepository repository;

  @BeforeEach
  void setup() {
    this.dao = new RecordingDao();
    this.repository = new DatabaseAuditEventRepository(this.dao);
  }

  @Test
  void testConstructorNullDao() {
    assertThatThrownBy(() -> new DatabaseAuditEventRepository(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("dao must not be null");
  }

  @Test
  void testAddDelegatesToDao() {
    final AuditEvent event = new AuditEvent("alice", "login", Map.of());
    this.repository.add(event);
    assertThat(this.dao.saved).containsExactly(event);
  }

  @Test
  void testAddRespectsFilter() {
    final DatabaseAuditEventRepository filtered = new DatabaseAuditEventRepository(this.dao,
        AbstractAuditEventRepository.inclusionPredicate(List.of("login")));
    filtered.add(new AuditEvent("a", "logout", Map.of()));
    filtered.add(new AuditEvent("a", "login", Map.of()));
    assertThat(this.dao.saved).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testStructuredFindDelegatesToDao() {
    this.repository.find("alice", null, "login");
    assertThat(this.dao.findCalls).containsExactly(new FindCall("alice", null, "login"));
  }

  @Test
  void testPredicateFindUsesRecentWindow() {
    this.dao.recent = List.of(new AuditEvent("alice", "login", Map.of()), new AuditEvent("bob", "login", Map.of()));
    final List<AuditEvent> result = this.repository.find(ExtendedAuditEventRepository.principal("alice"));
    assertThat(result).extracting(AuditEvent::getPrincipal).containsExactly("alice");
    assertThat(this.dao.lastLimit).isEqualTo(DatabaseAuditEventRepository.DEFAULT_MAX_FETCH);
  }

  @Test
  void testSetMaxFetch() {
    this.repository.setMaxFetch(5);
    this.repository.find(event -> true);
    assertThat(this.dao.lastLimit).isEqualTo(5);
  }

  @Test
  void testSetMaxFetchInvalid() {
    assertThatThrownBy(() -> this.repository.setMaxFetch(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxFetch must be greater than 0");
  }

  @Test
  void testSupportsFind() {
    assertThat(this.repository.supportsFind()).isTrue();
  }

  @Test
  void testWriteFailureThrowsByDefault() {
    final DatabaseAuditEventRepository repo = new DatabaseAuditEventRepository(throwingDao());
    assertThatThrownBy(() -> repo.add(new AuditEvent("alice", "login", Map.of())))
        .isInstanceOf(AuditEventWriteException.class);
  }

  @Test
  void testWriteFailureLoggedWhenConfigured() {
    final DatabaseAuditEventRepository repo = new DatabaseAuditEventRepository(throwingDao());
    repo.setThrowOnWriteFail(false);
    assertThatCode(() -> repo.add(new AuditEvent("alice", "login", Map.of())))
        .doesNotThrowAnyException();
  }

  @Test
  void testWriteFailureIsLoggedEvenWhenThrowing() {
    final ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AbstractAuditEventRepository.class);
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      final DatabaseAuditEventRepository repo = new DatabaseAuditEventRepository(throwingDao());
      assertThatThrownBy(() -> repo.add(new AuditEvent("alice", "login", Map.of())))
          .isInstanceOf(AuditEventWriteException.class);
      assertThat(appender.list)
          .anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("login"));
    }
    finally {
      logger.detachAppender(appender);
    }
  }

  private static AuditEventDao throwingDao() {
    return new AuditEventDao() {
      @Override
      public void save(final @NonNull AuditEvent event) {
        throw new IllegalStateException("db down");
      }

      @Override
      public @NonNull List<AuditEvent> find(final String principal, final Instant after, final String type) {
        return List.of();
      }

      @Override
      public @NonNull List<AuditEvent> findRecent(final int limit) {
        return List.of();
      }
    };
  }

  /**
   * A recording {@link AuditEventDao} fake.
   */
  private static class RecordingDao implements AuditEventDao {

    private final List<AuditEvent> saved = new ArrayList<>();

    private final List<FindCall> findCalls = new ArrayList<>();

    private List<AuditEvent> recent = List.of();

    private int lastLimit = -1;

    @Override
    public void save(final @NonNull AuditEvent event) {
      this.saved.add(event);
    }

    @Override
    public @NonNull List<AuditEvent> find(
        final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {
      this.findCalls.add(new FindCall(principal, after, type));
      return List.of();
    }

    @Override
    public @NonNull List<AuditEvent> findRecent(final int limit) {
      this.lastLimit = limit;
      return this.recent;
    }
  }

  private record FindCall(@Nullable String principal, @Nullable Instant after, @Nullable String type) {
  }
}
