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
package se.swedenconnect.spring.audit.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link ApplicationName}.
 *
 * @author Martin Lindström
 */
class ApplicationNameTest {

  @Test
  void testCreate() {
    final ApplicationName applicationName = new ApplicationName("test-app");

    assertThat(applicationName.getName()).isEqualTo("test-app");
    assertThat(applicationName).hasToString("test-app");
  }

  @Test
  void testNullNameThrows() {
    assertThatNullPointerException().isThrownBy(() -> new ApplicationName(null));
  }

  @Test
  void testEqualsAndHashCode() {
    final ApplicationName applicationName = new ApplicationName("test-app");

    assertThat(applicationName).isEqualTo(applicationName);
    assertThat(applicationName).isEqualTo(new ApplicationName("test-app"));
    assertThat(applicationName).hasSameHashCodeAs(new ApplicationName("test-app"));
    assertThat(applicationName).isNotEqualTo(new ApplicationName("other-app"));
    assertThat(applicationName).isNotEqualTo(null);
    assertThat(applicationName.equals("test-app")).isFalse();
  }

}
