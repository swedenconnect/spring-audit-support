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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link DefaultMongoAuditEventDao}, exercised against a MongoDB container.
 *
 * @author Martin Lindström
 */
@Testcontainers
class DefaultMongoAuditEventDaoTest {

  private static final String COLLECTION = "audit_events";

  private static final AuditEventMapper MAPPER = new JsonAuditEventMapper(JsonMapper.builder().build());

  @Container
  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

  private static MongoClient mongoClient;

  private static MongoTemplate mongoTemplate;

  private DefaultMongoAuditEventDao dao;

  @BeforeAll
  static void initMongo() {
    mongoClient = MongoClients.create(MONGO.getReplicaSetUrl());
    mongoTemplate = new MongoTemplate(mongoClient, "audit");
  }

  @AfterAll
  static void closeMongo() {
    mongoClient.close();
  }

  @BeforeEach
  void setup() {
    mongoTemplate.dropCollection(COLLECTION);
    this.dao = new DefaultMongoAuditEventDao(mongoTemplate, MAPPER, COLLECTION);
  }

  @Test
  void testSaveAndFindRecentNewestFirst() {
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("logout", "alice", "2026-01-01T11:00:00Z"));
    assertThat(this.dao.findRecent(10))
        .extracting(AuditEvent::getType)
        .containsExactly("logout", "login");
  }

  @Test
  void testRoundTripPreservesStructuredFields() {
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));

    final AuditEvent read = this.dao.findRecent(1).get(0);
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
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("login", "bob", "2026-01-01T11:00:00Z"));
    assertThat(this.dao.find("alice", null, null))
        .extracting(AuditEvent::getPrincipal)
        .containsExactly("alice");
  }

  @Test
  void testFindByType() {
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("logout", "alice", "2026-01-01T11:00:00Z"));
    assertThat(this.dao.find(null, null, "logout"))
        .extracting(AuditEvent::getType)
        .containsExactly("logout");
  }

  @Test
  void testFindByAfter() {
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("login", "alice", "2026-01-02T10:00:00Z"));
    assertThat(this.dao.find(null, Instant.parse("2026-01-01T12:00:00Z"), null))
        .extracting(AuditEvent::getTimestamp)
        .containsExactly(Instant.parse("2026-01-02T10:00:00Z"));
  }

  @Test
  void testFindCombinedCriteria() {
    this.dao.save(event("login", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("logout", "alice", "2026-01-01T11:00:00Z"));
    this.dao.save(event("login", "bob", "2026-01-01T12:00:00Z"));
    assertThat(this.dao.find("alice", null, "login"))
        .extracting(AuditEvent::getPrincipal, AuditEvent::getType)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("alice", "login"));
  }

  @Test
  void testFindRecentLimit() {
    this.dao.save(event("a", "alice", "2026-01-01T10:00:00Z"));
    this.dao.save(event("b", "alice", "2026-01-01T11:00:00Z"));
    this.dao.save(event("c", "alice", "2026-01-01T12:00:00Z"));
    assertThat(this.dao.findRecent(2))
        .extracting(AuditEvent::getType)
        .containsExactly("c", "b");
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
