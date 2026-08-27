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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test cases for {@link RedisAuditEventRepository}, exercised against a Redis container.
 *
 * @author Martin Lindström
 */
@Testcontainers
class RedisAuditEventRepositoryTest {

  private static final String KEY = "audit:events";

  private static final AuditEventMapper MAPPER = new JsonAuditEventMapper(JsonMapper.builder().build());

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;

  private static StringRedisTemplate redisTemplate;

  private RedisAuditEventRepository repository;

  @BeforeAll
  static void initRedis() {
    connectionFactory = new LettuceConnectionFactory(
        new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void closeRedis() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setup() {
    redisTemplate.delete(KEY);
    this.repository = new RedisAuditEventRepository(redisTemplate, KEY, MAPPER);
  }

  @Test
  void testConstructorNullArguments() {
    assertThatThrownBy(() -> new RedisAuditEventRepository(null, KEY, MAPPER))
        .isInstanceOf(NullPointerException.class).hasMessage("redisTemplate must not be null");
    assertThatThrownBy(() -> new RedisAuditEventRepository(redisTemplate, null, MAPPER))
        .isInstanceOf(NullPointerException.class).hasMessage("keyName must not be null");
    assertThatThrownBy(() -> new RedisAuditEventRepository(redisTemplate, KEY, null))
        .isInstanceOf(NullPointerException.class).hasMessage("mapper must not be null");
  }

  @Test
  void testSupportsFind() {
    assertThat(this.repository.supportsFind()).isTrue();
  }

  @Test
  void testAddAndFindNewestFirst() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("logout", "alice", "2026-01-01T11:00:00Z"));
    assertThat(this.repository.find(null, null, null))
        .extracting(AuditEvent::getType)
        .containsExactly("logout", "login");
  }

  @Test
  void testRoundTripPreservesStructuredFields() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));

    final AuditEvent read = this.repository.find(null, null, null).get(0);
    assertThat(read).isInstanceOf(se.swedenconnect.spring.audit.AuditEvent.class);
    final se.swedenconnect.spring.audit.AuditEvent structured = (se.swedenconnect.spring.audit.AuditEvent) read;
    assertThat(structured.getType()).isEqualTo("login");
    assertThat(structured.getPrincipal()).isEqualTo("alice");
    assertThat(structured.getTimestamp()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    assertThat(structured.getApplicationName()).isEqualTo(new ApplicationName("app"));
    assertThat(structured.getCorrelationId()).isEqualTo(new CorrelationID("corr-login"));
  }

  @Test
  void testFindByPrincipal() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("login", "bob", "2026-01-01T11:00:00Z"));
    assertThat(this.repository.find("alice", null, null))
        .extracting(AuditEvent::getPrincipal)
        .containsExactly("alice");
  }

  @Test
  void testFindByType() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("logout", "alice", "2026-01-01T11:00:00Z"));
    assertThat(this.repository.find(null, null, "logout"))
        .extracting(AuditEvent::getType)
        .containsExactly("logout");
  }

  @Test
  void testFindByAfter() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("login", "alice", "2026-01-02T10:00:00Z"));
    assertThat(this.repository.find(null, Instant.parse("2026-01-01T12:00:00Z"), null))
        .extracting(AuditEvent::getTimestamp)
        .containsExactly(Instant.parse("2026-01-02T10:00:00Z"));
  }

  @Test
  void testFindCombinedCriteria() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("logout", "alice", "2026-01-01T11:00:00Z"));
    this.repository.add(event("login", "bob", "2026-01-01T12:00:00Z"));
    assertThat(this.repository.find("alice", null, "login"))
        .extracting(AuditEvent::getPrincipal, AuditEvent::getType)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("alice", "login"));
  }

  @Test
  void testAddRespectsFilter() {
    final RedisAuditEventRepository filtered = new RedisAuditEventRepository(redisTemplate, KEY, MAPPER,
        AbstractAuditEventRepository.inclusionPredicate(List.of("login")));
    filtered.add(event("logout", "alice", "2026-01-01T10:00:00Z"));
    filtered.add(event("login", "alice", "2026-01-01T11:00:00Z"));
    assertThat(filtered.find(null, null, null))
        .extracting(AuditEvent::getType)
        .containsExactly("login");
  }

  @Test
  void testPredicateFind() {
    this.repository.add(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("login", "bob", "2026-01-01T11:00:00Z"));
    assertThat(this.repository.find(ExtendedAuditEventRepository.principal("alice")))
        .extracting(AuditEvent::getPrincipal)
        .containsExactly("alice");
  }

  @Test
  void testMaxFetchCapsPredicateFind() {
    this.repository.setMaxFetch(2);
    this.repository.add(event("a", "alice", "2026-01-01T10:00:00Z"));
    this.repository.add(event("b", "alice", "2026-01-01T11:00:00Z"));
    this.repository.add(event("c", "alice", "2026-01-01T12:00:00Z"));
    assertThat(this.repository.find(event -> true))
        .extracting(AuditEvent::getType)
        .containsExactly("c", "b");
  }

  @Test
  void testSetMaxFetchInvalid() {
    assertThatThrownBy(() -> this.repository.setMaxFetch(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxFetch must be greater than 0");
  }

  private static AuditEvent event(final String type, final String principal, final String timestamp) {
    return AuditEventBuilder.builder()
        .type(type)
        .principal(principal)
        .timestamp(Instant.parse(timestamp))
        .applicationName("app")
        .correlationId("corr-" + type)
        .build();
  }
}
