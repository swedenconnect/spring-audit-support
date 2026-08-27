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
package se.swedenconnect.spring.audit.appevents;

import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventContext;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.support.ApplicationName;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link SystemAlertEvent}.
 *
 * @author Martin Lindström
 */
class SystemAlertEventTest {

  private static AuditEventContext context() {
    final DefaultAuditEventContextResolver resolver =
        new DefaultAuditEventContextResolver(new ApplicationName("test-app"));
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);
    return resolver.getContext(null);
  }

  private static Map<String, Object> alertInfo(final AuditEvent auditEvent) {
    return (Map<String, Object>) auditEvent.getData().get("alert_info");
  }

  @Test
  void testNullMessageThrows() {
    assertThatNullPointerException().isThrownBy(() -> new SystemAlertEvent(null));
  }

  @Test
  void testAlertWithoutException() {
    final SystemAlertEvent event = new SystemAlertEvent("Disk is almost full");

    assertThat(event.getMessage()).isEqualTo("Disk is almost full");
    assertThat(event.getException()).isNull();

    final AuditEvent auditEvent = (AuditEvent) event.transform(event, context());

    assertThat(auditEvent.getType()).isEqualTo("system_alert");
    assertThat(auditEvent.getTimestamp()).isEqualTo(Instant.ofEpochMilli(event.getTimestamp()));
    assertThat(auditEvent.getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
    assertThat(auditEvent.getApplicationName()).isEqualTo(new ApplicationName("test-app"));

    assertThat(alertInfo(auditEvent))
        .containsEntry("message", "Disk is almost full")
        .containsEntry("exception_class", null)
        .containsEntry("exception_message", null);
  }

  @Test
  void testAlertWithException() {
    final Exception exception = new IllegalStateException("No connection");
    final SystemAlertEvent event = new SystemAlertEvent("Database is unreachable", exception);

    assertThat(event.getException()).isSameAs(exception);

    final AuditEvent auditEvent = (AuditEvent) event.transform(event, context());

    assertThat(alertInfo(auditEvent))
        .containsEntry("message", "Database is unreachable")
        .containsEntry("exception_class", IllegalStateException.class.getName())
        .containsEntry("exception_message", "No connection");
  }

  @Test
  void testSupportsAndEventType() {
    final SystemAlertEvent event = new SystemAlertEvent("Alert");

    assertThat(event.getEventType()).isEqualTo(SystemAlertEvent.class);
    assertThat(event.supports(event)).isTrue();
    assertThat(event.supports(new ContextClosedEvent(new StaticApplicationContext()))).isFalse();
  }

  @Test
  void testTransformOfUnsupportedEventThrows() {
    final SystemAlertEvent event = new SystemAlertEvent("Alert");
    final ContextClosedEvent other = new ContextClosedEvent(new StaticApplicationContext());
    final AuditEventContext context = context();

    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> event.transform(other, context));
  }

}
