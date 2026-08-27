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
package se.swedenconnect.spring.audit.value;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Test cases for {@link AuditValue}.
 *
 * @author Martin Lindström
 */
class AuditValueTest {

  private static final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void testNameAndValue() {
    final AuditValue<String> value = new AuditValue<>("key", "value");
    assertThat(value.getName()).isEqualTo("key");
    assertThat(value.getValue()).isEqualTo("value");
  }

  @Test
  void testNullValue() {
    final AuditValue<String> value = new AuditValue<>("key", null);
    assertThat(value.getValue()).isNull();
    assertThat(value.toMap()).containsEntry("key", null);
  }

  @Test
  void testToMap() {
    assertThat(new AuditValue<>("key", "value").toMap())
        .containsExactly(entry("key", "value"));
  }

  @Test
  void testToString() {
    assertThat(new AuditValue<>("key", "value")).hasToString("key=value");
  }

  @Test
  void testJsonSerialization() {
    final StringAuditValue value = new StringAuditValue("key", "value");
    assertThat(mapper.writeValueAsString(value)).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  void testNestedJsonSerialization() {
    final LinkedHashMap<String, AuditValue<? extends Serializable>> inner = new LinkedHashMap<>();
    inner.put("a", new StringAuditValue("a", "1"));
    final AuditValue<LinkedHashMap<String, AuditValue<? extends Serializable>>> complex =
        new AuditValue<>("complex", inner);
    assertThat(mapper.writeValueAsString(complex))
        .isEqualTo("{\"complex\":{\"a\":{\"a\":\"1\"}}}");
  }

  @Test
  void testConstructorNullName() {
    assertThatThrownBy(() -> new AuditValue<>(null, "value"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testBuilder() {
    final AuditValue<String> value = AuditValue.<String> builder()
        .name("key")
        .value("value")
        .build();
    assertThat(value.getName()).isEqualTo("key");
    assertThat(value.getValue()).isEqualTo("value");
    assertThat(value.toMap()).containsExactly(entry("key", "value"));
  }

  @Test
  void testBuilderNullValue() {
    final AuditValue<String> value = AuditValue.<String> builder()
        .name("key")
        .build();
    assertThat(value.getName()).isEqualTo("key");
    assertThat(value.getValue()).isNull();
  }

  @Test
  void testBuilderValueReplacedByLastAssignment() {
    final AuditValue<String> value = AuditValue.<String> builder()
        .name("key")
        .value("first")
        .value("second")
        .build();
    assertThat(value.getValue()).isEqualTo("second");
  }

  @Test
  void testBuilderNameIsRequired() {
    assertThatThrownBy(() -> AuditValue.<String> builder().value("value").build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must be assigned");
  }

  @Test
  void testBuilderNullName() {
    assertThatThrownBy(() -> AuditValue.<String> builder().name(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  /**
   * Test cases for {@link StringAuditValue}.
   */
  @Nested
  class StringAuditValueTest {

    @Test
    void testValue() {
      final StringAuditValue value = new StringAuditValue("key", "value");
      assertThat(value.getName()).isEqualTo("key");
      assertThat(value.getValue()).isEqualTo("value");
    }

    @Test
    void testNullValue() {
      assertThat(new StringAuditValue("key", null).getValue()).isNull();
    }

    @Test
    void testJsonSerialization() {
      assertThat(mapper.writeValueAsString(new StringAuditValue("key", "value")))
          .isEqualTo("{\"key\":\"value\"}");
    }
  }

  /**
   * Test cases for {@link IntegerAuditValue}.
   */
  @Nested
  class IntegerAuditValueTest {

    @Test
    void testValue() {
      final IntegerAuditValue value = new IntegerAuditValue("count", 42);
      assertThat(value.getName()).isEqualTo("count");
      assertThat(value.getValue()).isEqualTo(42);
    }

    @Test
    void testNullValue() {
      assertThat(new IntegerAuditValue("count", null).getValue()).isNull();
    }

    @Test
    void testJsonSerialization() {
      assertThat(mapper.writeValueAsString(new IntegerAuditValue("count", 42)))
          .isEqualTo("{\"count\":42}");
    }
  }

  /**
   * Test cases for {@link BooleanAuditValue}.
   */
  @Nested
  class BooleanAuditValueTest {

    @Test
    void testValue() {
      final BooleanAuditValue value = new BooleanAuditValue("enabled", true);
      assertThat(value.getName()).isEqualTo("enabled");
      assertThat(value.getValue()).isTrue();
    }

    @Test
    void testNullValue() {
      assertThat(new BooleanAuditValue("enabled", null).getValue()).isNull();
    }

    @Test
    void testJsonSerialization() {
      assertThat(mapper.writeValueAsString(new BooleanAuditValue("enabled", false)))
          .isEqualTo("{\"enabled\":false}");
    }
  }

  /**
   * Test cases for {@link InstantAuditValue}.
   */
  @Nested
  class InstantAuditValueTest {

    private static final Instant INSTANT = Instant.ofEpochMilli(1_600_000_000_000L);

    @Test
    void testCurrentTime() {
      final Instant before = Instant.now();
      final InstantAuditValue value = new InstantAuditValue("ts");
      final Instant after = Instant.now();
      assertThat(value.getValue()).isBetween(before, after);
    }

    @Test
    void testInstant() {
      assertThat(new InstantAuditValue("ts", INSTANT).getValue()).isEqualTo(INSTANT);
    }

    @Test
    void testNullInstant() {
      assertThat(new InstantAuditValue("ts", (Instant) null).getValue()).isNull();
    }

    @Test
    void testDate() {
      assertThat(new InstantAuditValue("ts", Date.from(INSTANT)).getValue()).isEqualTo(INSTANT);
    }

    @Test
    void testNullDate() {
      assertThat(new InstantAuditValue("ts", (Date) null).getValue()).isNull();
    }

    @Test
    void testClock() {
      assertThat(new InstantAuditValue("ts", Clock.fixed(INSTANT, ZoneOffset.UTC)).getValue())
          .isEqualTo(INSTANT);
    }

    @Test
    void testNullClock() {
      assertThat(new InstantAuditValue("ts", (Clock) null).getValue()).isNull();
    }
  }
}
