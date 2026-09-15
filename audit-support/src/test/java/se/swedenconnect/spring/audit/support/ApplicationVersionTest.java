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
 * Test cases for {@link ApplicationVersion}.
 *
 * @author Martin Lindström
 */
class ApplicationVersionTest {

  @Test
  void testCreate() {
    final ApplicationVersion applicationVersion = new ApplicationVersion("1.2.3");

    assertThat(applicationVersion.getVersion()).isEqualTo("1.2.3");
    assertThat(applicationVersion).hasToString("1.2.3");
  }

  @Test
  void testNullVersionThrows() {
    assertThatNullPointerException().isThrownBy(() -> new ApplicationVersion(null));
  }

  @Test
  void testEqualsAndHashCode() {
    final ApplicationVersion applicationVersion = new ApplicationVersion("1.2.3");

    assertThat(applicationVersion).isEqualTo(applicationVersion);
    assertThat(applicationVersion).isEqualTo(new ApplicationVersion("1.2.3"));
    assertThat(applicationVersion).hasSameHashCodeAs(new ApplicationVersion("1.2.3"));
    assertThat(applicationVersion).isNotEqualTo(new ApplicationVersion("1.2.4"));
    assertThat(applicationVersion).isNotEqualTo(null);
    assertThat(applicationVersion.equals("1.2.3")).isFalse();
  }

}
