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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Test cases for {@link InMemoryAuditEventRepository}.
 *
 * @author Martin Lindström
 */
class InMemoryAuditEventRepositoryTest {

  private static AuditEvent event(final String type) {
    return new AuditEvent(Instant.now(), "alice", type, Map.of());
  }

  @Test
  void testDefaultCapacity() {
    final InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
    repository.add(event("login"));

    assertThat(repository.supportsFind()).isTrue();
    assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testCapacity() {
    final InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository(1);
    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("logout");
  }

  @Test
  void testZeroCapacityThrows() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new InMemoryAuditEventRepository(0));
  }

  @Test
  void testSetCapacity() {
    final InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
    repository.setCapacity(1);
    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("logout");
  }

  @Test
  void testFilter() {
    final InMemoryAuditEventRepository repository =
        new InMemoryAuditEventRepository(ExtendedAuditEventRepository.type("login"));
    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
  }

  @Test
  void testCapacityAndFilter() {
    final InMemoryAuditEventRepository repository =
        new InMemoryAuditEventRepository(10, ExtendedAuditEventRepository.type("login"));
    repository.add(event("login"));
    repository.add(event("logout"));

    assertThat(repository.find(ExtendedAuditEventRepository.principal("alice")))
        .extracting(AuditEvent::getType).containsExactly("login");
    assertThat(repository.find(ExtendedAuditEventRepository.principal("bob"))).isEmpty();
  }

}
