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
package se.swedenconnect.spring.audit.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link CorrelationID}.
 *
 * @author Martin Lindström
 */
class CorrelationIDTest {

  @Test
  void testCreate() {
    assertThat(CorrelationID.of("abc-123").getValue()).isEqualTo("abc-123");
    assertThat(CorrelationID.of("abc-123")).hasToString("abc-123");
  }

  @Test
  void testNullValueThrows() {
    assertThatNullPointerException().isThrownBy(() -> new CorrelationID(null));
  }

  @Test
  void testEmptyValueThrows() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CorrelationID(" "));
  }

  @Test
  void testGenerate() {
    final CorrelationID id = CorrelationID.generate();
    assertThat(id.getValue()).isNotBlank();
    assertThat(CorrelationID.generate()).isNotEqualTo(id);
  }

  @Test
  void testEqualsAndHashCode() {
    final CorrelationID id = CorrelationID.of("abc-123");

    assertThat(id).isEqualTo(id);
    assertThat(id).isEqualTo(CorrelationID.of("abc-123"));
    assertThat(id).hasSameHashCodeAs(CorrelationID.of("abc-123"));
    assertThat(id).isNotEqualTo(CorrelationID.of("other"));
    assertThat(id).isNotEqualTo(null);
    assertThat(id.equals("abc-123")).isFalse();
  }

}
