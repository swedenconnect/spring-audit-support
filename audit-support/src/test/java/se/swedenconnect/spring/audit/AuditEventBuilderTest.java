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
import se.swedenconnect.spring.audit.support.Application;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.ApplicationVersion;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.tracing.TraceID;
import se.swedenconnect.spring.audit.value.StringAuditValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link AuditEventBuilder}.
 *
 * @author Martin Lindström
 */
class AuditEventBuilderTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-07-21T10:15:30.00Z");

  @Test
  void testBuilderReturnsNewInstance() {
    assertThat(AuditEventBuilder.builder())
        .isNotNull()
        .isNotSameAs(AuditEventBuilder.builder());
  }

  @Test
  void testMethodsAreFluent() {
    final AuditEventBuilder builder = AuditEventBuilder.builder();
    assertThat(builder.type(AuditType.of("login"))).isSameAs(builder);
    assertThat(builder.timestamp(TIMESTAMP)).isSameAs(builder);
    assertThat(builder.principal("alice")).isSameAs(builder);
    assertThat(builder.applicationVersion(new ApplicationVersion("1.2.3"))).isSameAs(builder);
    assertThat(builder.correlationId(CorrelationID.generate())).isSameAs(builder);
    assertThat(builder.rootField(new StringAuditValue("r", "v"))).isSameAs(builder);
    assertThat(builder.dataField(new StringAuditValue("d", "v"))).isSameAs(builder);
  }

  @Test
  void testBuildFull() {
    final CorrelationID correlationId = CorrelationID.generate();
    final AuditEvent event = AuditEventBuilder.builder()
        .type(AuditType.of("user-login"))
        .timestamp(TIMESTAMP)
        .principal("alice")
        .correlationId(correlationId)
        .rootField(new StringAuditValue("client_ip", "1.2.3.4"))
        .dataField(new StringAuditValue("method", "password"))
        .build();

    assertThat(event.getType()).isEqualTo("user-login");
    assertThat(event.getTimestamp()).isEqualTo(TIMESTAMP);
    assertThat(event.getPrincipal()).isEqualTo("alice");
    assertThat(event.getCorrelationId()).isEqualTo(correlationId);
    assertThat(event.getRootFields()).containsEntry("client_ip", "1.2.3.4");
    assertThat(event.getData()).containsEntry("method", "password");
  }

  @Test
  void testBuildWithoutTypeThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> AuditEventBuilder.builder().build())
        .withMessageContaining("type");
  }

  @Test
  void testTypeFromString() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("session-expired")
        .build();
    assertThat(event.getType()).isEqualTo("session-expired");
  }

  @Test
  void testTypeNullThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> AuditEventBuilder.builder().type((AuditType) null));
    assertThatNullPointerException()
        .isThrownBy(() -> AuditEventBuilder.builder().type((String) null));
  }

  @Test
  void testTimestampDefaultsToNow() {
    final Instant before = Instant.now();
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .build();
    assertThat(event.getTimestamp())
        .isBetween(before, Instant.now());
  }

  @Test
  void testTimestampFromDate() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .timestamp(Date.from(TIMESTAMP))
        .build();
    assertThat(event.getTimestamp()).isEqualTo(TIMESTAMP);
  }

  @Test
  void testTimestampFromClock() {
    final Clock clock = Clock.fixed(TIMESTAMP, ZoneOffset.UTC);
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .timestamp(clock)
        .build();
    assertThat(event.getTimestamp()).isEqualTo(TIMESTAMP);
  }

  @Test
  void testNullTimestampDefaultsToNow() {
    final Instant before = Instant.now();
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .timestamp((Instant) null)
        .timestamp((Date) null)
        .timestamp((Clock) null)
        .build();
    assertThat(event.getTimestamp())
        .isBetween(before, Instant.now());
  }

  @Test
  void testCorrelationIdFromString() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .correlationId("abc-123")
        .build();
    assertThat(event.getCorrelationId()).isEqualTo(new CorrelationID("abc-123"));
  }

  @Test
  void testRootFieldsReplacesPreviouslyAdded() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .rootField(new StringAuditValue("discarded", "x"))
        .rootFields(List.of(
            new StringAuditValue("a", "1"),
            new StringAuditValue("b", "2")))
        .build();
    assertThat(event.getRootFields())
        .containsOnlyKeys("a", "b")
        .containsEntry("a", "1")
        .containsEntry("b", "2");
  }

  @Test
  void testDataFieldsReplacesPreviouslyAdded() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .dataField(new StringAuditValue("discarded", "x"))
        .dataFields(List.of(
            new StringAuditValue("a", "1"),
            new StringAuditValue("b", "2")))
        .build();
    assertThat(event.getData())
        .containsOnlyKeys("a", "b")
        .containsEntry("a", "1")
        .containsEntry("b", "2");
  }

  @Test
  void testMultipleFieldsAreAccumulated() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .rootField(new StringAuditValue("r1", "v1"))
        .rootField(new StringAuditValue("r2", "v2"))
        .dataField(new StringAuditValue("d1", "v1"))
        .dataField(new StringAuditValue("d2", "v2"))
        .build();
    assertThat(event.getRootFields()).containsOnlyKeys("r1", "r2");
    assertThat(event.getData()).containsOnlyKeys("d1", "d2");
  }

  @Test
  void testTraceId() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .traceId("0af7651916cd43dd8448eb211c80319c")
        .build();

    assertThat(event.getTraceId()).isEqualTo(TraceID.of("0af7651916cd43dd8448eb211c80319c"));
  }

  @Test
  void testTraceIdAsValueType() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .traceId(TraceID.of("0af7651916cd43dd8448eb211c80319c"))
        .build();

    assertThat(event.getTraceId()).isEqualTo(TraceID.of("0af7651916cd43dd8448eb211c80319c"));
  }

  @Test
  void testStringValuesMayBeNull() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .applicationName((String) null)
        .applicationVersion((String) null)
        .correlationId((String) null)
        .traceId((String) null)
        .build();

    assertThat(event.getApplication()).isNull();
    assertThat(event.getCorrelationId()).isNull();
    assertThat(event.getTraceId()).isNull();
  }

  @Test
  void testNoApplicationGivesNoApplicationObject() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .build();

    assertThat(event.getApplication()).isNull();
  }

  @Test
  void testApplicationNameOnly() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .applicationName("my-app")
        .build();

    assertThat(event.getApplication()).isEqualTo(new Application(new ApplicationName("my-app"), null));
  }

  @Test
  void testApplicationVersionFromString() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .applicationVersion("1.2.3")
        .build();

    assertThat(event.getApplication()).isEqualTo(new Application(null, new ApplicationVersion("1.2.3")));
  }

  @Test
  void testApplicationVersionAsValueType() {
    final AuditEvent event = AuditEventBuilder.builder()
        .type("login")
        .applicationVersion(new ApplicationVersion("1.2.3"))
        .build();

    assertThat(event.getApplication()).isEqualTo(new Application(null, new ApplicationVersion("1.2.3")));
  }

  @Test
  void testBuilderFromContext() {
    final AuditEvent event = AuditEventBuilder.builder(
            context(new ApplicationName("my-app"), new ApplicationVersion("1.2.3")))
        .type("login")
        .build();

    assertThat(event.getApplication())
        .isEqualTo(new Application(new ApplicationName("my-app"), new ApplicationVersion("1.2.3")));
    assertThat(event.getCorrelationId()).isEqualTo(CorrelationID.of("corr-123"));
    assertThat(event.getPrincipal()).isEqualTo("alice");
  }

  @Test
  void testBuilderFromContextWithoutApplicationVersion() {
    final AuditEvent event = AuditEventBuilder.builder(context(new ApplicationName("my-app"), null))
        .type("login")
        .build();

    assertThat(event.getApplication()).isEqualTo(new Application(new ApplicationName("my-app"), null));
  }

  @Test
  void testApplicationVersionFromContextMayBeOverridden() {
    final AuditEvent event = AuditEventBuilder.builder(
            context(new ApplicationName("my-app"), new ApplicationVersion("1.2.3")))
        .type("login")
        .applicationVersion("4.5.6")
        .build();

    assertThat(event.getApplication())
        .isEqualTo(new Application(new ApplicationName("my-app"), new ApplicationVersion("4.5.6")));
  }

  /**
   * Creates an {@link AuditEventContext} holding the supplied application name and version.
   *
   * @param applicationName the application name
   * @param applicationVersion the application version (may be null)
   * @return an {@link AuditEventContext}
   */
  private static AuditEventContext context(
      final ApplicationName applicationName, final ApplicationVersion applicationVersion) {
    return new AuditEventContext() {

      @Override
      public ApplicationName getApplicationName() {
        return applicationName;
      }

      @Override
      public ApplicationVersion getApplicationVersion() {
        return applicationVersion;
      }

      @Override
      public CorrelationID getCorrelationId() {
        return CorrelationID.of("corr-123");
      }

      @Override
      public TraceID getTraceId() {
        return TraceID.of("4bf92f3577b34da6a3ce929d0e0e4736");
      }

      @Override
      public String getPrincipal() {
        return "alice";
      }
    };
  }

}
