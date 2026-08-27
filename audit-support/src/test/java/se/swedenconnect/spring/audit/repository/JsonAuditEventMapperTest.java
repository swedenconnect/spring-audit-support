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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.value.MapAuditValue;
import se.swedenconnect.spring.audit.value.StringAuditValue;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Test cases for {@link JsonAuditEventMapper}.
 *
 * @author Martin Lindström
 */
class JsonAuditEventMapperTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-08-02T10:15:30Z");

  private final JsonAuditEventMapper mapper = new JsonAuditEventMapper(JsonMapper.builder().build());

  @BeforeEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void testConstructorNullMapper() {
    assertThatThrownBy(() -> new JsonAuditEventMapper(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("mapper must not be null");
  }

  @Test
  void testWriteIncludesStructuredFields() {
    final String json = this.mapper.write(fullEvent());
    assertThat(json)
        .contains("\"type\":\"login\"")
        .contains("\"application_name\":\"my-app\"")
        .contains("\"correlation_id\":\"corr-123\"")
        .contains("\"principal\":\"alice\"")
        .contains("\"session\":\"S1\"")
        .contains("\"data\":{");
  }

  @Test
  void testWriteProducesExactJson() {
    // Guards the wire format of a structured audit event - the JSON must not change when the Java types of the
    // fields change.
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .timestamp(TIMESTAMP)
        .applicationName("my-app")
        .correlationId("corr-123")
        .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
        .principal("alice")
        .rootField(new StringAuditValue("session", "S1"))
        .dataField(new StringAuditValue("ip", "1.2.3.4"))
        .build();

    assertThat(this.mapper.write(event)).isEqualTo("{\"type\":\"login\","
        + "\"timestamp\":\"2026-08-02T10:15:30Z\","
        + "\"application_name\":\"my-app\","
        + "\"correlation_id\":\"corr-123\","
        + "\"trace_id\":\"4bf92f3577b34da6a3ce929d0e0e4736\","
        + "\"principal\":\"alice\","
        + "\"data\":{\"ip\":\"1.2.3.4\"},"
        + "\"session\":\"S1\"}");
  }

  @Test
  void testReadReturnsStructuredEvent() {
    final AuditEvent event = (AuditEvent) this.mapper.read(this.mapper.write(fullEvent()));

    assertThat(event.getType()).isEqualTo("login");
    assertThat(event.getPrincipal()).isEqualTo("alice");
    assertThat(event.getTimestamp()).isEqualTo(TIMESTAMP);
    assertThat(event.getApplicationName()).isEqualTo(new ApplicationName("my-app"));
    assertThat(event.getCorrelationId()).isEqualTo(new CorrelationID("corr-123"));
    assertThat(event.getData()).containsEntry("ip", "1.2.3.4").containsKey("http");
    assertThat(event.getRootFields()).containsExactly(entry("session", "S1"));
  }

  @Test
  void testRoundTrip() {
    final String json = this.mapper.write(fullEvent());
    final String roundTripped = this.mapper.write(this.mapper.read(json));
    assertThat(roundTripped).isEqualTo(json);
  }

  @Test
  void testRoundTripMinimalEvent() {
    final AuditEvent original = AuditEventBuilder.builder()
        .type("system_shutdown")
        .principal("system")
        .build();
    final String json = this.mapper.write(original);

    final AuditEvent event = (AuditEvent) this.mapper.read(json);
    assertThat(event.getType()).isEqualTo("system_shutdown");
    assertThat(event.getPrincipal()).isEqualTo("system");
    assertThat(event.getApplicationName()).isNull();
    assertThat(event.getCorrelationId()).isNull();
    assertThat(event.getRootFields()).isEmpty();

    assertThat(this.mapper.write(event)).isEqualTo(json);
  }

  @Test
  void testReadMissingTimestampDefaultsToNow() {
    final Instant before = Instant.now();
    final AuditEvent event =
        (AuditEvent) this.mapper.read("{\"type\":\"system_alert\",\"principal\":\"system\",\"data\":{}}");
    assertThat(event.getTimestamp()).isBetween(before, Instant.now());
  }

  @Test
  void testWritePlainSpringAuditEvent() {
    final org.springframework.boot.actuate.audit.AuditEvent spring =
        new org.springframework.boot.actuate.audit.AuditEvent("bob", "login", Map.of("k", "v"));

    final AuditEvent event = (AuditEvent) this.mapper.read(this.mapper.write(spring));
    assertThat(event.getPrincipal()).isEqualTo("bob");
    assertThat(event.getType()).isEqualTo("login");
    assertThat(event.getApplicationName()).isNull();
    assertThat(event.getCorrelationId()).isNull();
    assertThat(event.getData()).containsEntry("k", "v");
  }

  @Test
  void testReadInvalidJsonThrowsMappingException() {
    assertThatThrownBy(() -> this.mapper.read("{ this is not json"))
        .isInstanceOf(AuditEventMappingException.class)
        .hasMessage("Failed to deserialize audit event")
        .cause().isInstanceOf(tools.jackson.core.JacksonException.class);
  }

  private static AuditEvent fullEvent() {
    return AuditEventBuilder.builder()
        .type("login")
        .timestamp(TIMESTAMP)
        .applicationName("my-app")
        .correlationId("corr-123")
        .principal("alice")
        .rootField(new StringAuditValue("session", "S1"))
        .dataField(new MapAuditValue("http", Map.of("method", "GET")))
        .dataField(new StringAuditValue("ip", "1.2.3.4"))
        .build();
  }

  @Test
  void testWriteFailureThrowsMappingException() {
    final org.springframework.boot.actuate.audit.AuditEvent event =
        new org.springframework.boot.actuate.audit.AuditEvent("alice", "login", Map.of("failing", new FailingValue()));

    assertThatThrownBy(() -> this.mapper.write(event))
        .isInstanceOf(AuditEventMappingException.class)
        .hasMessage("Failed to serialize audit event");
  }

  /**
   * A value that Jackson can not serialize.
   */
  public static class FailingValue {

    public String getValue() {
      throw new IllegalStateException("Can not be serialized");
    }
  }

}
