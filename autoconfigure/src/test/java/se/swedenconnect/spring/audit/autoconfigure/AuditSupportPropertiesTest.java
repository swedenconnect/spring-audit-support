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
package se.swedenconnect.spring.audit.autoconfigure;

import org.junit.jupiter.api.Test;
import se.swedenconnect.spring.audit.AuditEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link AuditSupportProperties}.
 *
 * @author Martin Lindström
 */
class AuditSupportPropertiesTest {

  @Test
  void testNoDefaultPrincipalIsAssigned() {
    assertThat(new AuditSupportProperties().getDefaultPrincipal()).isNull();
  }

  @Test
  void testDefaultPrincipal() {
    final AuditSupportProperties properties = new AuditSupportProperties();
    properties.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);

    assertThat(properties.getDefaultPrincipal()).isEqualTo("system");

    properties.setDefaultPrincipal(null);
    assertThat(properties.getDefaultPrincipal()).isNull();
  }

  @Test
  void testLifecycleEventsAreLoggedByDefault() {
    assertThat(new AuditSupportProperties().isLogLifecycleEvents()).isTrue();
  }

  @Test
  void testLogLifecycleEvents() {
    final AuditSupportProperties properties = new AuditSupportProperties();
    properties.setLogLifecycleEvents(false);

    assertThat(properties.isLogLifecycleEvents()).isFalse();
  }

  @Test
  void testPrefix() {
    assertThat(AuditSupportProperties.PREFIX).isEqualTo("audit");
  }

}
