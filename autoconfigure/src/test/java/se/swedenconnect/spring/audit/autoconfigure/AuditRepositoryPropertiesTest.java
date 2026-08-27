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
import se.swedenconnect.spring.audit.repository.DefaultJdbcAuditEventDao;
import se.swedenconnect.spring.audit.repository.DefaultMongoAuditEventDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Test cases for {@link AuditRepositoryProperties}, i.e., for the validation and defaulting performed by
 * {@code afterPropertiesSet}.
 *
 * @author Martin Lindström
 */
class AuditRepositoryPropertiesTest {

  @Test
  void testNothingIsConfiguredByDefault() {
    final AuditRepositoryProperties properties = new AuditRepositoryProperties();
    properties.afterPropertiesSet();

    assertThat(properties.getInMemory()).isNull();
    assertThat(properties.getFile()).isNull();
    assertThat(properties.getJdbc()).isNull();
    assertThat(properties.getMongo()).isNull();
    assertThat(properties.getRedis()).isNull();
    assertThat(properties.getSyslog()).isNull();
    assertThat(properties.getIncludeEvents()).isEmpty();
    assertThat(properties.getExcludeEvents()).isEmpty();
    assertThat(properties.getThrowOnWriteFail()).isNull();
  }

  @Test
  void testEnabledDefaultsToTrue() {
    assertThat(new AuditRepositoryProperties.InMemory().isEnabled()).isTrue();
    assertThat(new AuditRepositoryProperties.File().isEnabled()).isTrue();
    assertThat(new AuditRepositoryProperties.Jdbc().isEnabled()).isTrue();
    assertThat(new AuditRepositoryProperties.Mongo().isEnabled()).isTrue();
    assertThat(new AuditRepositoryProperties.Redis().isEnabled()).isTrue();
    assertThat(new AuditRepositoryProperties.Syslog().isEnabled()).isTrue();
  }

  @Test
  void testEventListsAreModifiable() {
    final AuditRepositoryProperties properties = new AuditRepositoryProperties();
    properties.getIncludeEvents().add("login");
    properties.getExcludeEvents().add("noisy_event");

    assertThat(properties.getIncludeEvents()).containsExactly("login");
    assertThat(properties.getExcludeEvents()).containsExactly("noisy_event");
  }

  @Test
  void testAllConfiguredSectionsAreValidated() {
    final AuditRepositoryProperties properties = new AuditRepositoryProperties();
    properties.setFile(new AuditRepositoryProperties.File());

    // The file section is configured, but its required log file is missing.
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(properties::afterPropertiesSet)
        .withMessageContaining("audit.repository.file.log-file");
  }

  @Test
  void testInMemoryCapacityIsOptionalButMustBePositive() {
    final AuditRepositoryProperties.InMemory inMemory = new AuditRepositoryProperties.InMemory();
    assertThatCode(inMemory::afterPropertiesSet).doesNotThrowAnyException();
    assertThat(inMemory.getCapacity()).isNull();

    inMemory.setCapacity(0);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(inMemory::afterPropertiesSet)
        .withMessageContaining("audit.repository.in-memory.capacity");

    inMemory.setCapacity(-1);
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(inMemory::afterPropertiesSet);

    inMemory.setCapacity(10);
    assertThatCode(inMemory::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void testFileRequiresALogFile() {
    final AuditRepositoryProperties.File file = new AuditRepositoryProperties.File();

    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(file::afterPropertiesSet);

    file.setLogFile(" ");
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(file::afterPropertiesSet);

    file.setLogFile("/var/log/audit.log");
    assertThatCode(file::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void testJdbcTableNameIsDefaulted() {
    final AuditRepositoryProperties.Jdbc jdbc = new AuditRepositoryProperties.Jdbc();
    jdbc.afterPropertiesSet();

    assertThat(jdbc.getTableName()).isEqualTo(DefaultJdbcAuditEventDao.DEFAULT_TABLE_NAME);
  }

  @Test
  void testAssignedJdbcTableNameIsKept() {
    final AuditRepositoryProperties.Jdbc jdbc = new AuditRepositoryProperties.Jdbc();
    jdbc.setTableName("my_events");
    jdbc.afterPropertiesSet();

    assertThat(jdbc.getTableName()).isEqualTo("my_events");
  }

  @Test
  void testMongoCollectionIsDefaulted() {
    final AuditRepositoryProperties.Mongo mongo = new AuditRepositoryProperties.Mongo();
    mongo.afterPropertiesSet();

    assertThat(mongo.getCollection()).isEqualTo(DefaultMongoAuditEventDao.DEFAULT_COLLECTION_NAME);
  }

  @Test
  void testRedisKeyIsDefaulted() {
    final AuditRepositoryProperties.Redis redis = new AuditRepositoryProperties.Redis();
    redis.afterPropertiesSet();

    assertThat(redis.getKey()).isEqualTo(AuditRepositoryProperties.Redis.DEFAULT_KEY);
  }

  @Test
  void testSyslogRequiresAHost() {
    final AuditRepositoryProperties.Syslog syslog = new AuditRepositoryProperties.Syslog();

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(syslog::afterPropertiesSet)
        .withMessageContaining("audit.repository.syslog.host");
  }

  @Test
  void testSyslogTransportIsValidated() {
    final AuditRepositoryProperties.Syslog syslog = new AuditRepositoryProperties.Syslog();
    syslog.setHost("127.0.0.1");

    syslog.setTransport("sctp");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(syslog::afterPropertiesSet)
        .withMessageContaining("audit.repository.syslog.transport");

    for (final String transport : new String[] { "udp", "UDP", "tcp", "TCP" }) {
      syslog.setTransport(transport);
      assertThatCode(syslog::afterPropertiesSet).doesNotThrowAnyException();
    }
  }

  @Test
  void testSyslogPortIsValidated() {
    final AuditRepositoryProperties.Syslog syslog = new AuditRepositoryProperties.Syslog();
    syslog.setHost("127.0.0.1");

    syslog.setPort(0);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(syslog::afterPropertiesSet)
        .withMessageContaining("audit.repository.syslog.port");

    syslog.setPort(65536);
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(syslog::afterPropertiesSet);

    syslog.setPort(65535);
    assertThatCode(syslog::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void testDisabledSectionsAreNotValidated() {
    final AuditRepositoryProperties.File file = new AuditRepositoryProperties.File();
    file.setEnabled(false);

    final AuditRepositoryProperties.Syslog syslog = new AuditRepositoryProperties.Syslog();
    syslog.setEnabled(false);
    syslog.setTransport("sctp");
    syslog.setPort(70000);

    final AuditRepositoryProperties.InMemory inMemory = new AuditRepositoryProperties.InMemory();
    inMemory.setEnabled(false);
    inMemory.setCapacity(0);

    final AuditRepositoryProperties properties = new AuditRepositoryProperties();
    properties.setFile(file);
    properties.setSyslog(syslog);
    properties.setInMemory(inMemory);

    assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void testDisabledSectionsAreNotDefaultedEither() {
    final AuditRepositoryProperties.Redis redis = new AuditRepositoryProperties.Redis();
    redis.setEnabled(false);
    redis.afterPropertiesSet();

    assertThat(redis.getKey()).isNull();
  }

}
