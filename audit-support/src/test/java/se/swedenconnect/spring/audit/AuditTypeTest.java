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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Test cases for {@link AuditType}.
 *
 * @author Martin Lindström
 */
class AuditTypeTest {

  @Test
  void testCreate() {
    assertThat(AuditType.of("login").type()).isEqualTo("login");
    assertThat(AuditType.of("login")).hasToString("login");
  }

  @Test
  void testNullTypeThrows() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new AuditType(null));
  }

  @Test
  void testEmptyTypeThrows() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new AuditType(" "));
  }

  @Test
  void testEqualsAndHashCode() {
    final AuditType type = AuditType.of("login");

    assertThat(type).isEqualTo(type);
    assertThat(type).isEqualTo(AuditType.of("login"));
    assertThat(type).hasSameHashCodeAs(AuditType.of("login"));
    assertThat(type).isNotEqualTo(AuditType.of("logout"));
    assertThat(type).isNotEqualTo(null);
    assertThat(type.equals("login")).isFalse();
  }

}
