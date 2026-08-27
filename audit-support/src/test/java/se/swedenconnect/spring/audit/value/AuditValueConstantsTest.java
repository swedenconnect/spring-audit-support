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

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link AuditValueConstants}.
 *
 * @author Martin Lindström
 */
class AuditValueConstantsTest {

  @Test
  void testStringValues() {
    assertThat(AuditValueConstants.userId("alice"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("user_id"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("alice"));

    assertThat(AuditValueConstants.displayName("Alice Smith"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("display_name"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("Alice Smith"));

    assertThat(AuditValueConstants.givenName("Alice"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("given_name"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("Alice"));

    assertThat(AuditValueConstants.surname("Smith"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("sn"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("Smith"));

    assertThat(AuditValueConstants.email("alice@example.com"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("email"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("alice@example.com"));

    assertThat(AuditValueConstants.personalIdentityNumber("196911292032"))
        .satisfies(v -> assertThat(v.getName()).isEqualTo("personal_identity_number"))
        .satisfies(v -> assertThat(v.getValue()).isEqualTo("196911292032"));
  }

  @Test
  void testNullStringValues() {
    assertThat(AuditValueConstants.userId(null).getValue()).isNull();
    assertThat(AuditValueConstants.displayName(null).getValue()).isNull();
    assertThat(AuditValueConstants.givenName(null).getValue()).isNull();
    assertThat(AuditValueConstants.surname(null).getValue()).isNull();
    assertThat(AuditValueConstants.email(null).getValue()).isNull();
    assertThat(AuditValueConstants.personalIdentityNumber(null).getValue()).isNull();
  }

  @Test
  void testError() {
    final MapAuditValue error =
        AuditValueConstants.error("E1", "Something failed", IllegalStateException.class, "details");

    assertThat(error.getName()).isEqualTo("error");
    assertThat(new LinkedHashMap<String, Object>(error.getValue()))
        .containsEntry("code", "E1")
        .containsEntry("message", "Something failed")
        .containsEntry("exception_class", IllegalStateException.class.getName())
        .containsEntry("details", "details");
  }

  @Test
  void testErrorWithOnlyCode() {
    final MapAuditValue error = AuditValueConstants.error("E1", null, null, null);

    assertThat(new LinkedHashMap<String, Object>(error.getValue()))
        .containsEntry("code", "E1")
        .containsEntry("message", null)
        .containsEntry("exception_class", null)
        .containsEntry("details", null);
  }

}
