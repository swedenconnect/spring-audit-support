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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link FileBasedAuditEventRepository}.
 *
 * @author Martin Lindström
 */
class FileBasedAuditEventRepositoryTest {

  private static final AuditEventMapper MAPPER = new JsonAuditEventMapper(JsonMapper.builder().build());

  @TempDir
  private Path directory;

  @Test
  void testConstructorNullMapper() {
    assertThatThrownBy(() -> new FileBasedAuditEventRepository(this.logFile().toString(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("eventMapper must not be null");
  }

  @Test
  void testConstructorDirectoryPath() {
    assertThatThrownBy(() -> new FileBasedAuditEventRepository(this.directory.toString(), MAPPER))
        .isInstanceOf(IOException.class)
        .hasMessage("Given logFile points to a directory and not a file");
  }

  @Test
  void testConstructorCreatesParentDirectories() throws Exception {
    final Path logFile = this.directory.resolve("sub/nested/audit.log");
    final FileBasedAuditEventRepository repository = new FileBasedAuditEventRepository(logFile.toString(), MAPPER);
    repository.add(event("login", "alice"));
    assertThat(Files.exists(logFile)).isTrue();
  }

  @Test
  void testAddWritesJsonLine() throws Exception {
    final Path logFile = this.logFile();
    final FileBasedAuditEventRepository repository = new FileBasedAuditEventRepository(logFile.toString(), MAPPER);

    repository.add(event("login", "alice"));

    final List<String> lines = Files.readAllLines(logFile);
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst())
        .contains("\"type\":\"login\"")
        .contains("\"principal\":\"alice\"");
  }

  @Test
  void testMultipleEventsAreAppended() throws Exception {
    final Path logFile = this.logFile();
    final FileBasedAuditEventRepository repository = new FileBasedAuditEventRepository(logFile.toString(), MAPPER);

    repository.add(event("login", "alice"));
    repository.add(event("logout", "bob"));

    assertThat(Files.readAllLines(logFile))
        .hasSize(2)
        .satisfies(lines -> {
          assertThat(lines.getFirst()).contains("\"type\":\"login\"");
          assertThat(lines.get(1)).contains("\"type\":\"logout\"");
        });
  }

  @Test
  void testAddRespectsFilter() throws Exception {
    final Path logFile = this.logFile();
    final FileBasedAuditEventRepository repository = new FileBasedAuditEventRepository(logFile.toString(), MAPPER,
        AbstractAuditEventRepository.inclusionPredicate(List.of("login")));

    repository.add(event("logout", "alice"));
    repository.add(event("login", "alice"));

    final List<String> lines = Files.readAllLines(logFile);
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst()).contains("\"type\":\"login\"");
  }

  @Test
  void testDoesNotSupportFind() throws Exception {
    final FileBasedAuditEventRepository repository =
        new FileBasedAuditEventRepository(this.logFile().toString(), MAPPER);
    assertThat(repository.supportsFind()).isFalse();
    assertThat(repository.find(null, null, null)).isEmpty();
    assertThat(repository.find(event -> true)).isEmpty();
  }

  @Test
  void testRecreatedRepositoryReplacesHandler() throws Exception {
    final Path logFile = this.logFile();

    // Creating a second repository for the same file must replace the handler of the first, so that an event is not
    // written twice.
    new FileBasedAuditEventRepository(logFile.toString(), MAPPER);
    final FileBasedAuditEventRepository repository = new FileBasedAuditEventRepository(logFile.toString(), MAPPER);
    repository.add(event("login", "alice"));

    assertThat(Files.readAllLines(logFile)).hasSize(1);
    try (final Stream<Path> files = Files.list(logFile.getParent())) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .noneMatch(name -> name.matches("audit\\.log\\.\\d+"));
    }
  }

  @Test
  void testGetEventsThrows() throws Exception {
    final TestRepository repository = new TestRepository(this.logFile().toString(), MAPPER);

    assertThatThrownBy(repository::events)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("This repository does not support find");
  }

  @Test
  void testWriteFailureThrowsByDefault() throws Exception {
    final FileBasedAuditEventRepository repository =
        new FileBasedAuditEventRepository(this.logFile().toString(), failingMapper());
    assertThatThrownBy(() -> repository.add(event("login", "alice")))
        .isInstanceOf(AuditEventWriteException.class);
  }

  @Test
  void testWriteFailureLoggedWhenConfigured() throws Exception {
    final FileBasedAuditEventRepository repository =
        new FileBasedAuditEventRepository(this.logFile().toString(), failingMapper());
    repository.setThrowOnWriteFail(false);
    assertThatCode(() -> repository.add(event("login", "alice"))).doesNotThrowAnyException();
  }

  private static AuditEventMapper failingMapper() {
    return new AuditEventMapper() {
      @Override
      public @NonNull String write(final @NonNull AuditEvent event) {
        throw new AuditEventMappingException("boom");
      }

      @Override
      public @NonNull AuditEvent read(final @NonNull String event) {
        throw new AuditEventMappingException("boom");
      }
    };
  }

  private Path logFile() {
    return this.directory.resolve("audit.log");
  }

  private static AuditEvent event(final String type, final String principal) {
    return AuditEventBuilder.builder()
        .type(type)
        .principal(principal)
        .timestamp(Instant.parse("2026-01-01T10:00:00Z"))
        .applicationName("app")
        .build();
  }

  /**
   * Exposes the protected {@code getEvents} method.
   */
  private static class TestRepository extends FileBasedAuditEventRepository {

    TestRepository(final String logFile, final AuditEventMapper eventMapper) throws IOException {
      super(logFile, eventMapper);
    }

    void events() {
      this.getEvents();
    }
  }

}
