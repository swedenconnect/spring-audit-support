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
package se.swedenconnect.spring.audit.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for the audit event filter predicates of {@link AbstractAuditEventRepository}.
 *
 * @author Martin Lindström
 */
class AuditEventFilterPredicateTest {

  private static AuditEvent event(final String type) {
    return new AuditEvent("alice", type, Map.of());
  }

  @Test
  void testInclusionPredicate() {
    assertThat(AbstractAuditEventRepository.inclusionPredicate(List.of("login", "logout")))
        .accepts(event("login"), event("logout"))
        .rejects(event("other"));
  }

  @Test
  void testEmptyInclusionPredicateAcceptsNothing() {
    assertThat(AbstractAuditEventRepository.inclusionPredicate(List.of())).rejects(event("login"));
  }

  @Test
  void testExclusionPredicate() {
    assertThat(AbstractAuditEventRepository.exclusionPredicate(List.of("login")))
        .accepts(event("logout"))
        .rejects(event("login"));
  }

  @Test
  void testEmptyExclusionPredicateAcceptsEverything() {
    assertThat(AbstractAuditEventRepository.exclusionPredicate(List.of())).accepts(event("login"));
  }

  @Test
  void testInclusionExclusionPredicate() {
    assertThat(AbstractAuditEventRepository.inclusionExclusionPredicate(
        List.of("login", "logout"), List.of("logout")))
        .accepts(event("login"))
        .rejects(event("logout"), event("other"));
  }

}
