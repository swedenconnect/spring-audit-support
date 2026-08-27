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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link ListAuditValue}.
 *
 * @author Martin Lindström
 */
class ListAuditValueTest {

  private static final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void testConstructorWithList() {
    final ListAuditValue lv = new ListAuditValue("lst", List.of("a", "b"));
    assertThat(lv.getName()).isEqualTo("lst");
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void testConstructorCopiesList() {
    final List<Serializable> source = new ArrayList<>();
    source.add("a");
    final ListAuditValue lv = new ListAuditValue("lst", source);
    source.add("b");
    assertThat(lv.getValue()).isEqualTo(List.of("a"));
  }

  @Test
  void testConstructorWithNullList() {
    final List<? extends Serializable> nullList = null;
    final ListAuditValue lv = new ListAuditValue("lst", nullList);
    assertThat(lv.getValue()).isNull();
  }

  @Test
  void testConstructorWithArray() {
    final ListAuditValue lv = new ListAuditValue("lst", new String[] { "a", "b" });
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void testConstructorWithNullArray() {
    final Serializable[] nullArray = null;
    final ListAuditValue lv = new ListAuditValue("lst", nullArray);
    assertThat(lv.getValue()).isNull();
  }

  @Test
  void testConstructorWithNullName() {
    assertThatThrownBy(() -> new ListAuditValue(null, List.of("a")))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testToMap() {
    final ListAuditValue lv = new ListAuditValue("lst", List.of("a", "b"));
    assertThat(lv.toMap()).hasSize(1).containsKey("lst");
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void testJsonSerialization() {
    final ListAuditValue lv = new ListAuditValue("lst", List.of("a", "b"));
    assertThat(mapper.writeValueAsString(lv)).isEqualTo("{\"lst\":[\"a\",\"b\"]}");
  }

  @Test
  void testBuilder() {
    final ListAuditValue lv = ListAuditValue.builder()
        .name("lst")
        .value("a")
        .value("b", "c")
        .build();
    assertThat(lv.getName()).isEqualTo("lst");
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b", "c"));
  }

  @Test
  void testBuilderEmpty() {
    final ListAuditValue lv = ListAuditValue.builder().name("lst").build();
    assertThat(lv.getValue()).isEqualTo(List.of());
  }

  @Test
  void testBuilderValues() {
    final ListAuditValue lv = ListAuditValue.builder()
        .name("lst")
        .values(List.of("a", "b"))
        .build();
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void testBuilderValuesNullClears() {
    final ListAuditValue lv = ListAuditValue.builder()
        .name("lst")
        .value("dropped")
        .values(null)
        .build();
    assertThat(lv.getValue()).isEqualTo(List.of());
  }

  @Test
  void testBuilderWholeListValueReplacesElements() {
    final ListAuditValue lv = ListAuditValue.builder()
        .name("lst")
        .value("dropped")
        .value(new LinkedList<>(List.of("a", "b")))
        .build();
    assertThat(lv.getValue()).isEqualTo(List.of("a", "b"));
  }

  @Test
  void testBuilderNameIsRequired() {
    assertThatThrownBy(() -> ListAuditValue.builder().value("a").build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must be assigned");
  }

  @Test
  void testBuilderNullName() {
    assertThatThrownBy(() -> ListAuditValue.builder().name(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
  }

  @Test
  void testBuilderNullElement() {
    assertThatThrownBy(() -> ListAuditValue.builder().name("lst").value("a", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("value must not be null");
  }

  @Test
  void testBuilderNullElementArray() {
    assertThatThrownBy(() -> ListAuditValue.builder().name("lst").value((Serializable[]) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("values must not be null");
  }
}
