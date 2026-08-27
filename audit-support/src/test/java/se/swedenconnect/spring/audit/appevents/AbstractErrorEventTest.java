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

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.AuditEventContext;
import se.swedenconnect.spring.audit.AuditType;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.value.AuditValueConstants;

import java.io.Serial;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link AbstractErrorEvent}.
 *
 * @author Martin Lindström
 */
class AbstractErrorEventTest {

  private static final AuditType ERROR_TYPE = AuditType.of("test_error");

  private static AuditEventContext context() {
    final DefaultAuditEventContextResolver resolver =
        new DefaultAuditEventContextResolver(new ApplicationName("test-app"));
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);
    return resolver.getContext(null);
  }

  private static Map<String, Object> error(final AuditEvent auditEvent) {
    return (Map<String, Object>) auditEvent.getData().get("error");
  }

  @Test
  void testNullCodeThrows() {
    assertThatNullPointerException().isThrownBy(() -> new TestErrorEvent(null, "message", null, null));
  }

  @Test
  void testGetError() {
    final TestErrorEvent event = new TestErrorEvent("E1", "Something failed", IllegalStateException.class, "details");

    assertThat(event.getError().code()).isEqualTo("E1");
    assertThat(event.getError().message()).isEqualTo("Something failed");
    assertThat(event.getError().exception()).isEqualTo(IllegalStateException.class);
    assertThat(event.getError().details()).isEqualTo("details");
  }

  @Test
  void testTransform() {
    final TestErrorEvent event = new TestErrorEvent("E1", "Something failed", IllegalStateException.class, "details");

    final AuditEvent auditEvent = (AuditEvent) event.transform(event, context());

    assertThat(auditEvent.getType()).isEqualTo(ERROR_TYPE.type());
    assertThat(auditEvent.getTimestamp()).isEqualTo(Instant.ofEpochMilli(event.getTimestamp()));
    assertThat(auditEvent.getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
    assertThat(auditEvent.getApplicationName()).isEqualTo(new ApplicationName("test-app"));

    assertThat(error(auditEvent))
        .containsEntry("code", "E1")
        .containsEntry("message", "Something failed")
        .containsEntry("exception_class", IllegalStateException.class.getName())
        .containsEntry("details", "details");
  }

  @Test
  void testTransformWithOptionalValuesMissing() {
    final TestErrorEvent event = new TestErrorEvent("E2", null, null, null);

    final AuditEvent auditEvent = (AuditEvent) event.transform(event, context());

    assertThat(error(auditEvent))
        .containsEntry("code", "E2")
        .containsEntry("message", null)
        .containsEntry("exception_class", null)
        .containsEntry("details", null);
  }

  @Test
  void testSubclassMayAddFields() {
    final TestErrorEvent event = new TestErrorEvent("E1", "Something failed", null, null);

    final AuditEvent auditEvent = (AuditEvent) event.transform(event, context());

    assertThat(auditEvent.getData()).containsKey("user_id");
  }

  @Test
  void testSupports() {
    final TestErrorEvent event = new TestErrorEvent("E1", null, null, null);

    assertThat(event.supports(event)).isTrue();
    assertThat(event.supports(new ContextClosedEvent(new StaticApplicationContext()))).isFalse();
  }

  @Test
  void testTransformOfUnsupportedEventThrows() {
    final TestErrorEvent event = new TestErrorEvent("E1", null, null, null);
    final ContextClosedEvent other = new ContextClosedEvent(new StaticApplicationContext());
    final AuditEventContext context = context();

    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> event.transform(other, context))
        .withMessageContaining(TestErrorEvent.class.getSimpleName());
  }

  /**
   * A concrete {@link AbstractErrorEvent} that adds a data field of its own.
   */
  private static class TestErrorEvent extends AbstractErrorEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    TestErrorEvent(final String code, final String message, final Class<?> exception, final String details) {
      super(code, message, exception, details);
    }

    @Override
    protected org.springframework.boot.actuate.audit.@NonNull AuditEvent transform(
        final @NonNull AuditEventBuilder eventBuilder, final @NonNull AuditEventContext context) {
      return eventBuilder
          .dataField(AuditValueConstants.userId("alice"))
          .build();
    }

    @Override
    protected @NonNull AuditType getAuditType() {
      return ERROR_TYPE;
    }
  }

}
