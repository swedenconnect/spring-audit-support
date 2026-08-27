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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * A write-only {@link ExtendedAuditEventRepository} that writes audit events to a file, one JSON event per line.
 * <p>
 * The file is rolled per date (UTC): when the first event of a new day is written, the current file is renamed to
 * {@code <name>-<yyyyMMdd>.<ext>} and a fresh file is started (see {@link DateRollingFileHandler}).
 * </p>
 * <p>
 * Being write-only, this repository does <b>not</b> support querying: {@link #supportsFind()} returns {@code false} and
 * the {@code find} methods return an empty list.
 * </p>
 * <p>
 * A failure to write an event is handled according to the repository's
 * {@link #setThrowOnWriteFail(boolean) write-failure policy}.
 * </p>
 *
 * @author Martin Lindström
 */
public class FileBasedAuditEventRepository extends AbstractAuditEventRepository {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(FileBasedAuditEventRepository.class);

  /** The audit logger (Java Util Logging logger). */
  private final java.util.logging.Logger auditLogger;

  /** For mapping events to strings. */
  private final AuditEventMapper eventMapper;

  /**
   * Constructor setting up the repository with no filtering.
   *
   * @param logFile the log file including its path
   * @param eventMapper the event mapper used to map events to strings
   * @throws IOException if the log file is invalid
   */
  public FileBasedAuditEventRepository(final @NonNull String logFile, final @NonNull AuditEventMapper eventMapper)
      throws IOException {
    this(logFile, eventMapper, null);
  }

  /**
   * Constructor.
   *
   * @param logFile the log file including its path
   * @param eventMapper the event mapper used to map events to strings
   * @param filter the filter (if {@code null}, no filtering is performed)
   * @throws IOException if the log file is invalid
   */
  public FileBasedAuditEventRepository(final @NonNull String logFile, final @NonNull AuditEventMapper eventMapper,
      final @Nullable Predicate<AuditEvent> filter) throws IOException {
    super(filter);
    this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");

    // Build the logger name based on the absolute log file name ...
    final String loggerName = Path.of(logFile).toAbsolutePath().toString();

    this.auditLogger = java.util.logging.Logger.getLogger(loggerName);
    this.auditLogger.setLevel(Level.INFO);
    // Replace any handler previously registered for this file, so that recreating the repository does not result in
    // duplicated log records. The previous handler must be closed before the new one is created - otherwise it still
    // holds the lock of the log file, and the new handler would end up writing to <log-file>.1.
    for (final Handler existing : this.auditLogger.getHandlers()) {
      existing.close();
      this.auditLogger.removeHandler(existing);
    }
    this.auditLogger.addHandler(new DateRollingFileHandler(logFile));
    this.auditLogger.setUseParentHandlers(false);
  }

  /** {@inheritDoc} */
  @Override
  protected void addEvent(final @NonNull AuditEvent event) {
    log.debug("Audit logging event '{}' for principal '{}' ...", event.getType(), event.getPrincipal());
    this.auditLogger.log(Level.INFO, this.eventMapper.write(event));
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code false}; this repository is write-only
   */
  @Override
  public boolean supportsFind() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always; this repository does not support find
   */
  @Override
  protected @NonNull Iterator<AuditEvent> getEvents() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This repository does not support find");
  }

}
