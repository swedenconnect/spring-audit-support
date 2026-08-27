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
package se.swedenconnect.spring.audit.transform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventContext;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.support.ApplicationName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link ApplicationReadyEventTransformer} and {@link ContextClosedEventTransformer}, which also cover
 * the default methods of {@link SingleEventTransformer}.
 *
 * @author Martin Lindström
 */
class SystemEventTransformerTest {

  private static AuditEventContext context() {
    final DefaultAuditEventContextResolver resolver =
        new DefaultAuditEventContextResolver(new ApplicationName("test-app"));
    return resolver.getContext(null);
  }

  private static ApplicationReadyEvent applicationReadyEvent() {
    final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    when(event.getTimestamp()).thenReturn(1000L);
    return event;
  }

  private static ContextClosedEvent contextClosedEvent() {
    return new ContextClosedEvent(new StaticApplicationContext());
  }

  @Test
  void testApplicationReadyEventIsTransformed() {
    final ApplicationReadyEventTransformer transformer = new ApplicationReadyEventTransformer();
    final ApplicationReadyEvent event = applicationReadyEvent();

    final AuditEvent auditEvent = (AuditEvent) transformer.transform(event, context());

    assertThat(auditEvent.getType()).isEqualTo("system_started");
    assertThat(auditEvent.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1000L));
    assertThat(auditEvent.getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
    assertThat(auditEvent.getApplicationName()).isEqualTo(new ApplicationName("test-app"));
  }

  @Test
  void testContextClosedEventIsTransformed() {
    final ContextClosedEventTransformer transformer = new ContextClosedEventTransformer();
    final ContextClosedEvent event = contextClosedEvent();

    final AuditEvent auditEvent = (AuditEvent) transformer.transform(event, context());

    assertThat(auditEvent.getType()).isEqualTo("system_shutdown");
    assertThat(auditEvent.getTimestamp()).isEqualTo(Instant.ofEpochMilli(event.getTimestamp()));
    assertThat(auditEvent.getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
    assertThat(auditEvent.getApplicationName()).isEqualTo(new ApplicationName("test-app"));
  }

  @Test
  void testEventTypes() {
    assertThat(new ApplicationReadyEventTransformer().getEventType()).isEqualTo(ApplicationReadyEvent.class);
    assertThat(new ContextClosedEventTransformer().getEventType()).isEqualTo(ContextClosedEvent.class);
  }

  @Test
  void testSupports() {
    final ApplicationReadyEventTransformer transformer = new ApplicationReadyEventTransformer();

    assertThat(transformer.supports(applicationReadyEvent())).isTrue();
    assertThat(transformer.supports(contextClosedEvent())).isFalse();
  }

  @Test
  void testTransformOfUnsupportedEventThrows() {
    final ApplicationReadyEventTransformer transformer = new ApplicationReadyEventTransformer();
    final ApplicationEvent event = contextClosedEvent();
    final AuditEventContext context = context();

    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> transformer.transform(event, context))
        .withMessageContaining(ContextClosedEvent.class.getName());
  }

}
