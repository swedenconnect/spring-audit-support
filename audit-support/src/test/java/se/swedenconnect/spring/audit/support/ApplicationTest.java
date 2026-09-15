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

/**
 * Test cases for {@link Application}.
 *
 * @author Martin Lindström
 */
class ApplicationTest {

  private static final ApplicationName NAME = new ApplicationName("my-app");

  private static final ApplicationVersion VERSION = new ApplicationVersion("1.2.3");

  @Test
  void testCreate() {
    final Application application = new Application(NAME, VERSION);

    assertThat(application.getName()).isEqualTo(NAME);
    assertThat(application.getVersion()).isEqualTo(VERSION);
    assertThat(application.isEmpty()).isFalse();
    assertThat(application).hasToString("my-app-1.2.3");
  }

  @Test
  void testNameOnly() {
    final Application application = new Application(NAME, null);

    assertThat(application.getName()).isEqualTo(NAME);
    assertThat(application.getVersion()).isNull();
    assertThat(application.isEmpty()).isFalse();
    assertThat(application).hasToString("my-app");
  }

  @Test
  void testVersionOnly() {
    final Application application = new Application(null, VERSION);

    assertThat(application.getName()).isNull();
    assertThat(application.getVersion()).isEqualTo(VERSION);
    assertThat(application.isEmpty()).isFalse();
    assertThat(application).hasToString("1.2.3");
  }

  @Test
  void testEmpty() {
    final Application application = new Application(null, null);

    assertThat(application.getName()).isNull();
    assertThat(application.getVersion()).isNull();
    assertThat(application.isEmpty()).isTrue();
    assertThat(application).hasToString("");
  }

  @Test
  void testEqualsAndHashCode() {
    final Application application = new Application(NAME, VERSION);

    assertThat(application).isEqualTo(application);
    assertThat(application).isEqualTo(new Application(NAME, VERSION));
    assertThat(application).hasSameHashCodeAs(new Application(NAME, VERSION));
    assertThat(application).isNotEqualTo(new Application(NAME, null));
    assertThat(application).isNotEqualTo(new Application(null, VERSION));
    assertThat(application).isNotEqualTo(new Application(new ApplicationName("other-app"), VERSION));
    assertThat(application).isNotEqualTo(null);
    assertThat(application.equals("my-app")).isFalse();
  }

}
