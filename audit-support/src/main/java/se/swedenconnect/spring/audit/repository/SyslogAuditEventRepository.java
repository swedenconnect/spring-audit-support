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

import com.cloudbees.syslog.sender.SyslogMessageSender;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * An {@link ExtendedAuditEventRepository} that sends audit events to a syslog server.
 * <p>
 * Each event is serialized to JSON (via the {@link AuditEventMapper}) and sent as the message body of a syslog message,
 * using a <a href="https://github.com/CloudBees-community/syslog-java-client">syslog-java-client</a>
 * {@link SyslogMessageSender}. The sender is supplied pre-configured, so the transport (UDP/TCP/TLS), server host and
 * port, syslog facility, severity, message format (RFC 3164 or RFC 5424) and application name are all controlled by the
 * caller &ndash; this repository only writes the event as the message body.
 * </p>
 * <p>
 * Syslog is a write-only sink, so this repository does <b>not</b> support querying: {@link #supportsFind()} returns
 * {@code false} and the {@code find} methods return an empty list.
 * </p>
 * <p>
 * A failure to send an event is handled according to the repository's
 * {@link #setThrowOnWriteFail(boolean) write-failure policy}.
 * </p>
 *
 * @author Martin Lindström
 */
public class SyslogAuditEventRepository extends AbstractAuditEventRepository {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(SyslogAuditEventRepository.class);

  /** The syslog message sender. */
  private final SyslogMessageSender messageSender;

  /** The audit event mapper. */
  private final AuditEventMapper eventMapper;

  /**
   * Constructor setting up the repository with no filtering.
   *
   * @param messageSender the pre-configured syslog message sender
   * @param mapper the mapper for creating JSON events
   */
  public SyslogAuditEventRepository(
      final @NonNull SyslogMessageSender messageSender, final @NonNull AuditEventMapper mapper) {
    this(messageSender, mapper, null);
  }

  /**
   * Constructor.
   *
   * @param messageSender the pre-configured syslog message sender
   * @param mapper the mapper for creating JSON events
   * @param filter the filter (if {@code null}, no filtering is performed)
   */
  public SyslogAuditEventRepository(final @NonNull SyslogMessageSender messageSender,
      final @NonNull AuditEventMapper mapper, final @Nullable Predicate<AuditEvent> filter) {
    super(filter);
    this.messageSender = Objects.requireNonNull(messageSender, "messageSender must not be null");
    this.eventMapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  /** {@inheritDoc} */
  @Override
  protected void addEvent(final @NonNull AuditEvent event) {
    log.debug("Audit logging event '{}' for principal '{}' ...", event.getType(), event.getPrincipal());
    try {
      this.messageSender.sendMessage(this.eventMapper.write(event));
    }
    catch (final IOException e) {
      throw new AuditEventWriteException("Failed to send audit event to syslog", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code false}; syslog is a write-only sink
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
