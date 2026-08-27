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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Test cases for {@link MapAuditValue}.
 *
 * @author Martin Lindström
 */
class MapAuditValueTest {

  private static final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void testConstructorWithMap() {
    final LinkedHashMap<String, Serializable> source = new LinkedHashMap<>();
    source.put("k1", "v1");
    source.put("k2", 42);
    final MapAuditValue mv = new MapAuditValue("obj", source);
    assertThat(mv.getName()).isEqualTo("obj");
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("k1", "v1"), entry("k2", 42));
  }

  @Test
  void testConstructorCopiesMap() {
    final Map<String, Serializable> source = new HashMap<>();
    source.put("k1", "v1");
    final MapAuditValue mv = new MapAuditValue("obj", source);
    source.put("k2", "v2");
    assertThat(new LinkedHashMap<String, Object>(mv.getValue())).containsOnly(entry("k1", "v1"));
  }

  @Test
  void testConstructorWithNullMap() {
    final MapAuditValue mv = new MapAuditValue("obj", (Map<String, ? extends Serializable>) null);
    assertThat(mv.getValue()).isNull();
  }

  @Test
  void testConstructorWithAuditValues() {
    final MapAuditValue mv = new MapAuditValue("obj",
        new StringAuditValue("k1", "v1"), new IntegerAuditValue("k2", 42));
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("k1", "v1"), entry("k2", 42));
  }

  @Test
  void testConstructorWithNullAuditValues() {
    final AuditValue<? extends Serializable>[] nullValues = null;
    final MapAuditValue mv = new MapAuditValue("obj", nullValues);
    assertThat(mv.getValue()).isNull();
  }

  @Test
  void testConstructorWithNoAuditValues() {
    final MapAuditValue mv = new MapAuditValue("obj");
    assertThat(mv.getValue()).isEmpty();
  }

  @Test
  void testConstructorWithNullName() {
    assertThatThrownBy(() -> new MapAuditValue(null, Map.of("k", "v")))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testToMap() {
    final MapAuditValue mv = new MapAuditValue("obj", new StringAuditValue("k", "v"));
    assertThat(mv.toMap()).hasSize(1).containsKey("obj");
    assertThat(new LinkedHashMap<String, Object>(mv.getValue())).containsExactly(entry("k", "v"));
  }

  @Test
  void testJsonSerialization() {
    final MapAuditValue mv = new MapAuditValue("obj",
        new StringAuditValue("a", "1"), new IntegerAuditValue("b", 2));
    assertThat(mapper.writeValueAsString(mv)).isEqualTo("{\"obj\":{\"a\":\"1\",\"b\":2}}");
  }

  @Test
  void testBuilder() {
    final MapAuditValue mv = MapAuditValue.builder()
        .name("obj")
        .value("k1", "v1")
        .value(new AuditValue<>("k2", 42))
        .build();
    assertThat(mv.getName()).isEqualTo("obj");
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("k1", "v1"), entry("k2", 42));
  }

  @Test
  void testBuilderEmpty() {
    final MapAuditValue mv = MapAuditValue.builder().name("obj").build();
    assertThat(mv.getValue()).isEmpty();
  }

  @Test
  void testBuilderNullMemberValue() {
    final MapAuditValue mv = MapAuditValue.builder()
        .name("obj")
        .value("k1", null)
        .build();
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("k1", null));
  }

  @Test
  void testBuilderValues() {
    final MapAuditValue mv = MapAuditValue.builder()
        .name("obj")
        .values(Map.of("a", "1"))
        .build();
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("a", "1"));
  }

  @Test
  void testBuilderValuesNullClears() {
    final MapAuditValue mv = MapAuditValue.builder()
        .name("obj")
        .value("dropped", "x")
        .values(null)
        .build();
    assertThat(mv.getValue()).isEmpty();
  }

  @Test
  void testBuilderWholeMapValueReplacesMembers() {
    final MapAuditValue mv = MapAuditValue.builder()
        .name("obj")
        .value("dropped", "x")
        .value(new LinkedHashMap<>(Map.of("a", "1")))
        .build();
    assertThat(new LinkedHashMap<String, Object>(mv.getValue()))
        .containsExactly(entry("a", "1"));
  }

  @Test
  void testBuilderNameIsRequired() {
    assertThatThrownBy(() -> MapAuditValue.builder().value("k", "v").build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must be assigned");
  }

  @Test
  void testBuilderNullName() {
    assertThatThrownBy(() -> MapAuditValue.builder().name(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testBuilderNullMemberName() {
    assertThatThrownBy(() -> MapAuditValue.builder().value(null, "v"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testBuilderNullAuditValue() {
    assertThatThrownBy(() -> MapAuditValue.builder().value((AuditValue<? extends Serializable>) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("value must not be null");
  }

  @Test
  void testGetSubValue() {
    final TestMapAuditValue mv = new TestMapAuditValue("map", Map.of("k1", "v1"));

    assertThat(mv.subValue("k1", String.class)).isEqualTo("v1");
    assertThat(mv.subValue("missing", String.class)).isNull();
  }

  @Test
  void testGetSubValueWhenValueIsNull() {
    final TestMapAuditValue mv = new TestMapAuditValue("map", null);

    assertThat(mv.subValue("k1", String.class)).isNull();
  }

  /**
   * Exposes the protected {@link MapAuditValue#getSubValue(String, Class)} method.
   */
  private static class TestMapAuditValue extends MapAuditValue {

    @Serial
    private static final long serialVersionUID = 1L;

    TestMapAuditValue(final String name, final Map<String, ? extends Serializable> value) {
      super(name, value);
    }

    <T extends Serializable> T subValue(final String name, final Class<T> type) {
      return this.getSubValue(name, type);
    }
  }

}
