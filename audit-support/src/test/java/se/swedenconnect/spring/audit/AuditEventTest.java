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
package se.swedenconnect.spring.audit;

import org.junit.jupiter.api.Test;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.tracing.TraceID;
import se.swedenconnect.spring.audit.value.AuditValue;
import se.swedenconnect.spring.audit.value.MapAuditValue;
import se.swedenconnect.spring.audit.value.StringAuditValue;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

/**
 * Test cases for {@link AuditEvent}.
 *
 * @author Martin Lindström
 */
class AuditEventTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-08-02T10:15:30Z");

  @Test
  void testFullEvent() {
    final AuditEvent event = new AuditEvent(AuditType.of("login"), TIMESTAMP,
        new ApplicationName("my-app"), CorrelationID.of("corr-123"), TraceID.of("trace-1"), "alice",
        List.of(new StringAuditValue("session", "S1")),
        List.of(new StringAuditValue("ip", "1.2.3.4"), new MapAuditValue("http", Map.of("method", "GET"))));

    assertThat(event.getType()).isEqualTo("login");
    assertThat(event.getTimestamp()).isEqualTo(TIMESTAMP);
    assertThat(event.getPrincipal()).isEqualTo("alice");
    assertThat(event.getApplicationName()).isEqualTo(new ApplicationName("my-app"));
    assertThat(event.getCorrelationId()).isEqualTo(CorrelationID.of("corr-123"));
    assertThat(event.getTraceId()).isEqualTo(TraceID.of("trace-1"));
    assertThat(event.getRootFields()).containsExactly(entry("session", "S1"));
    assertThat(event.getData()).containsKeys("ip", "http");
  }

  @Test
  void testMinimalEvent() {
    final AuditEvent event =
        new AuditEvent(AuditType.of("login"), TIMESTAMP, null, null, null, null, null, null);

    assertThat(event.getType()).isEqualTo("login");
    assertThat(event.getApplicationName()).isNull();
    assertThat(event.getCorrelationId()).isNull();
    assertThat(event.getTraceId()).isNull();
    // Spring's AuditEvent normalizes a missing principal to an empty string.
    assertThat(event.getPrincipal()).isEmpty();
    assertThat(event.getRootFields()).isEmpty();
    assertThat(event.getData()).isEmpty();
  }

  @Test
  void testNullTimestampGivesCurrentTime() {
    final Instant before = Instant.now();
    final AuditEvent event = new AuditEvent(AuditType.of("login"), null, null, null, null, null, null, null);

    assertThat(event.getTimestamp()).isBetween(before, Instant.now());
  }

  @Test
  void testNullTypeThrows() {
    assertThatNullPointerException().isThrownBy(
        () -> new AuditEvent(null, TIMESTAMP, null, null, null, null, null, null));
  }

  @Test
  void testDataFieldsKeepTheirOrder() {
    final List<AuditValue<? extends Serializable>> dataFields = List.of(
        new StringAuditValue("b", "2"),
        new StringAuditValue("a", "1"),
        new StringAuditValue("c", "3"));
    final AuditEvent event =
        new AuditEvent(AuditType.of("login"), TIMESTAMP, null, null, null, null, null, dataFields);

    assertThat(event.getData()).containsExactly(entry("b", "2"), entry("a", "1"), entry("c", "3"));
  }

  @Test
  void testRootFieldsKeepTheirOrder() {
    final List<AuditValue<? extends Serializable>> rootFields = List.of(
        new StringAuditValue("b", "2"),
        new StringAuditValue("a", "1"));
    final AuditEvent event =
        new AuditEvent(AuditType.of("login"), TIMESTAMP, null, null, null, null, rootFields, null);

    assertThat(event.getRootFields()).containsExactly(entry("b", "2"), entry("a", "1"));
  }

  @Test
  void testSystemPrincipal() {
    assertThat(AuditEvent.SYSTEM_PRINCIPAL).isEqualTo("system");
  }

  @Test
  void testIsASpringAuditEvent() {
    // The event must be usable everywhere Spring Boot's audit infrastructure expects an audit event.
    final AuditEvent event =
        new AuditEvent(AuditType.of("login"), TIMESTAMP, null, null, null, "alice", null, null);

    assertThat(event).isInstanceOf(org.springframework.boot.actuate.audit.AuditEvent.class);
  }

}
