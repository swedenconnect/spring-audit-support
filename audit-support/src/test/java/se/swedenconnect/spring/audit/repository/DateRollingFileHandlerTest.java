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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link DateRollingFileHandler}, focusing on the per-date rolling behavior (using a controllable
 * clock).
 *
 * @author Martin Lindström
 */
class DateRollingFileHandlerTest {

  @TempDir
  private Path directory;

  @Test
  void testRollsFilePerDay() throws Exception {
    final Path logFile = this.directory.resolve("audit.log");
    final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    final DateRollingFileHandler handler = new DateRollingFileHandler(logFile.toString(), clock);

    handler.publish(record("event-day1"));

    // Move to the next day and publish again - the day-1 file should be rolled to a dated backup.
    clock.set(Instant.parse("2026-01-02T10:00:00Z"));
    handler.publish(record("event-day2"));
    handler.flush();

    final Path backup = this.directory.resolve("audit-20260101.log");
    assertThat(Files.readAllLines(logFile)).containsExactly("event-day2");
    assertThat(Files.readAllLines(backup)).containsExactly("event-day1");

    handler.close();
  }

  @Test
  void testNoRollWithinSameDay() throws Exception {
    final Path logFile = this.directory.resolve("audit.log");
    final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    final DateRollingFileHandler handler = new DateRollingFileHandler(logFile.toString(), clock);

    handler.publish(record("event-1"));
    clock.set(Instant.parse("2026-01-01T23:59:59Z"));
    handler.publish(record("event-2"));
    handler.flush();

    assertThat(Files.readAllLines(logFile)).containsExactly("event-1", "event-2");
    try (final Stream<Path> files = Files.list(this.directory)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .noneMatch(name -> name.startsWith("audit-2026"));
    }

    handler.close();
  }

  @Test
  void testRollsFileWithoutExtension() throws Exception {
    final Path logFile = this.directory.resolve("audit");
    final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    final DateRollingFileHandler handler = new DateRollingFileHandler(logFile.toString(), clock);

    handler.publish(record("event-day1"));
    clock.set(Instant.parse("2026-01-02T10:00:00Z"));
    handler.publish(record("event-day2"));
    handler.flush();

    assertThat(Files.readAllLines(logFile)).containsExactly("event-day2");
    assertThat(Files.readAllLines(this.directory.resolve("audit-20260101"))).containsExactly("event-day1");

    handler.close();
  }

  @Test
  void testExistingFileIsAppendedTo() throws Exception {
    final Path logFile = this.directory.resolve("audit.log");
    Files.writeString(logFile, "existing-event\n");

    final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    final DateRollingFileHandler handler = new DateRollingFileHandler(logFile.toString(), clock);
    handler.publish(record("new-event"));
    handler.flush();

    assertThat(Files.readAllLines(logFile)).containsExactly("existing-event", "new-event");

    handler.close();
  }

  @Test
  void testMissingParentDirectoryIsCreated() throws Exception {
    final Path logFile = this.directory.resolve("logs").resolve("audit.log");

    final DateRollingFileHandler handler =
        new DateRollingFileHandler(logFile.toString(), new MutableClock(Instant.parse("2026-01-01T10:00:00Z")));
    handler.publish(record("event"));
    handler.flush();

    assertThat(Files.readAllLines(logFile)).containsExactly("event");

    handler.close();
  }

  @Test
  void testLogFilePointingToDirectoryThrows() {
    assertThatExceptionOfType(IOException.class)
        .isThrownBy(() -> new DateRollingFileHandler(this.directory.toString()))
        .withMessage("Given logFile points to a directory and not a file");
  }

  @Test
  void testNullArgumentsThrow() {
    assertThatNullPointerException()
        .isThrownBy(() -> new DateRollingFileHandler(null, Clock.systemUTC()));
    assertThatNullPointerException()
        .isThrownBy(() -> new DateRollingFileHandler(this.directory.resolve("audit.log").toString(), null));
  }

  @Test
  void testNullRecordIsIgnored() throws Exception {
    final Path logFile = this.directory.resolve("audit.log");
    final DateRollingFileHandler handler = new DateRollingFileHandler(logFile.toString());

    handler.publish(null);
    handler.flush();

    assertThat(Files.readAllLines(logFile)).isEmpty();

    handler.close();
  }

  private static LogRecord record(final String message) {
    return new LogRecord(Level.INFO, message);
  }

  /**
   * A {@link Clock} whose instant can be moved forward.
   */
  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(final Instant instant) {
      this.instant = instant;
    }

    private void set(final Instant instant) {
      this.instant = instant;
    }

    @Override
    public Instant instant() {
      return this.instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }
  }
}
