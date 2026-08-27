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

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.SyslogMessage;
import com.cloudbees.syslog.sender.SyslogMessageSender;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link SyslogAuditEventRepository}, using a local UDP socket as the syslog server.
 *
 * @author Martin Lindström
 */
class SyslogAuditEventRepositoryTest {

  private static final AuditEventMapper MAPPER = new JsonAuditEventMapper(JsonMapper.builder().build());

  private DatagramSocket server;

  private UdpSyslogMessageSender sender;

  private SyslogAuditEventRepository repository;

  @BeforeEach
  void setup() throws Exception {
    this.server = new DatagramSocket(0);
    this.server.setSoTimeout(2000);

    this.sender = new UdpSyslogMessageSender();
    this.sender.setSyslogServerHostname("127.0.0.1");
    this.sender.setSyslogServerPort(this.server.getLocalPort());
    this.sender.setDefaultAppName("audit");
    this.sender.setDefaultMessageHostname("localhost");
    this.sender.setDefaultFacility(Facility.LOCAL0);
    this.sender.setDefaultSeverity(Severity.INFORMATIONAL);
    this.sender.setMessageFormat(MessageFormat.RFC_5424);

    this.repository = new SyslogAuditEventRepository(this.sender, MAPPER);
  }

  @AfterEach
  void tearDown() {
    this.server.close();
  }

  @Test
  void testConstructorNullArguments() {
    assertThatThrownBy(() -> new SyslogAuditEventRepository(null, MAPPER))
        .isInstanceOf(NullPointerException.class).hasMessage("messageSender must not be null");
    assertThatThrownBy(() -> new SyslogAuditEventRepository(this.sender, null))
        .isInstanceOf(NullPointerException.class).hasMessage("mapper must not be null");
  }

  @Test
  void testAddSendsSyslogMessage() throws Exception {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));

    final String message = this.receive();
    assertThat(message)
        .contains("audit")                    // app name, part of the RFC 5424 header
        .contains("\"type\":\"login\"")        // the event JSON body
        .contains("\"principal\":\"alice\"");
  }

  @Test
  void testDoesNotSupportFind() {
    assertThat(this.repository.supportsFind()).isFalse();
    assertThat(this.repository.find(null, null, null)).isEmpty();
    assertThat(this.repository.find(event -> true)).isEmpty();
  }

  @Test
  void testAddRespectsFilter() throws Exception {
    final SyslogAuditEventRepository filtered = new SyslogAuditEventRepository(this.sender, MAPPER,
        AbstractAuditEventRepository.inclusionPredicate(List.of("login")));

    filtered.add(event("logout", "alice", "2026-01-01T10:00:00Z"));  // excluded - not sent
    filtered.add(event("login", "alice", "2026-01-01T11:00:00Z"));   // included - sent

    assertThat(this.receive()).contains("\"type\":\"login\"");

    // No second message should have been sent.
    this.server.setSoTimeout(300);
    assertThatThrownBy(this::receive).isInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void testSendFailureThrowsByDefault() {
    final SyslogAuditEventRepository repo = new SyslogAuditEventRepository(failingSender(), MAPPER);
    assertThatThrownBy(() -> repo.add(event("login", "alice", "2026-01-01T10:00:00Z")))
        .isInstanceOf(AuditEventWriteException.class);
  }

  @Test
  void testSendFailureLoggedWhenConfigured() {
    final SyslogAuditEventRepository repo = new SyslogAuditEventRepository(failingSender(), MAPPER);
    repo.setThrowOnWriteFail(false);
    assertThatCode(() -> repo.add(event("login", "alice", "2026-01-01T10:00:00Z")))
        .doesNotThrowAnyException();
  }

  private static SyslogMessageSender failingSender() {
    return new SyslogMessageSender() {
      @Override
      public void sendMessage(final CharSequence message) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void sendMessage(final @NonNull SyslogMessage message) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void sendMessage(final CharArrayWriter message) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void close() {
      }
    };
  }

  private String receive() throws IOException {
    final byte[] buffer = new byte[8192];
    final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    this.server.receive(packet);
    return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
  }

  private static AuditEvent event(final String type, final String principal, final String timestamp) {
    return AuditEventBuilder.builder()
        .type(type)
        .principal(principal)
        .timestamp(Instant.parse(timestamp))
        .applicationName("app")
        .correlationId("corr-" + type)
        .build();
  }

  @Test
  void testGetEventsThrows() {
    final TestRepository repository = new TestRepository(this.sender, MAPPER);

    assertThatThrownBy(repository::events).isInstanceOf(UnsupportedOperationException.class);
  }

  /**
   * Exposes the protected {@code getEvents} method.
   */
  private static class TestRepository extends SyslogAuditEventRepository {

    TestRepository(final com.cloudbees.syslog.sender.SyslogMessageSender sender, final AuditEventMapper mapper) {
      super(sender, mapper);
    }

    void events() {
      this.getEvents();
    }
  }

}
