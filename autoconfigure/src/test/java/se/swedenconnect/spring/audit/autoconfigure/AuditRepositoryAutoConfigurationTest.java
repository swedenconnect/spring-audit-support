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

import com.cloudbees.syslog.SyslogMessage;
import com.cloudbees.syslog.sender.SyslogMessageSender;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import se.swedenconnect.spring.audit.repository.AuditEventMapper;
import se.swedenconnect.spring.audit.repository.DefaultJdbcAuditEventDao;
import se.swedenconnect.spring.audit.repository.DefaultMongoAuditEventDao;
import se.swedenconnect.spring.audit.repository.JdbcAuditEventDao;

import javax.sql.DataSource;
import java.io.CharArrayWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test cases for {@link AuditRepositoryAutoConfiguration}.
 *
 * @author Martin Lindström
 */
class AuditRepositoryAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AuditRepositoryAutoConfiguration.class));

  @Test
  void testDefaultCreatesQueryableRepository() {
    this.runner.run(context -> {
      assertThat(context).hasSingleBean(AuditEventRepository.class);
      final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
      repository.add(new AuditEvent("alice", "login", Map.of()));
      assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
    });
  }

  @Test
  void testBacksOffWhenUserRepositoryPresent() {
    final AuditEventRepository userRepository =
        new org.springframework.boot.actuate.audit.InMemoryAuditEventRepository();
    this.runner.withBean(AuditEventRepository.class, () -> userRepository).run(context -> {
      assertThat(context).hasSingleBean(AuditEventRepository.class);
      assertThat(context.getBean(AuditEventRepository.class)).isSameAs(userRepository);
      assertThat(context).doesNotHaveBean(AuditEventMapper.class);
    });
  }

  @Test
  void testFileRepository(@TempDir final Path directory) {
    final Path logFile = directory.resolve("audit.log");
    this.runner.withPropertyValues("audit.repository.file.log-file=" + logFile)
        .run(context -> {
          final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
          repository.add(new AuditEvent("alice", "login", Map.of()));
          assertThat(Files.readAllLines(logFile)).hasSize(1);
          assertThat(Files.readAllLines(logFile).getFirst()).contains("\"type\":\"login\"");
        });
  }

  @Test
  void testFileRepositoryRequiresLogFile() {
    this.runner.withPropertyValues("audit.repository.file.log-file=").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context).getFailure().hasMessageContaining("audit.repository.file.log-file");
    });
  }

  @Test
  void testInvalidInMemoryCapacity() {
    this.runner.withPropertyValues("audit.repository.in-memory.capacity=0")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context).getFailure().hasMessageContaining("audit.repository.in-memory.capacity");
        });
  }

  @Test
  void testInMemoryCapacityIsUsed() {
    this.runner.withPropertyValues("audit.repository.in-memory.capacity=1")
        .run(context -> {
          final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
          repository.add(new AuditEvent("alice", "login", Map.of()));
          repository.add(new AuditEvent("alice", "logout", Map.of()));
          assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("logout");
        });
  }

  @Test
  void testInvalidSyslogTransport() {
    this.runner.withPropertyValues(
            "audit.repository.syslog.host=127.0.0.1",
            "audit.repository.syslog.transport=sctp")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context).getFailure().hasMessageContaining("audit.repository.syslog.transport");
        });
  }

  @Test
  void testInvalidSyslogPort() {
    this.runner.withPropertyValues(
            "audit.repository.syslog.host=127.0.0.1",
            "audit.repository.syslog.port=70000")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context).getFailure().hasMessageContaining("audit.repository.syslog.port");
        });
  }

  @Test
  void testSyslogTransportIsCaseInsensitive() {
    this.runner.withPropertyValues(
            "audit.repository.syslog.host=127.0.0.1",
            "audit.repository.syslog.transport=TCP")
        .run(context -> assertThat(context).hasSingleBean(AuditEventRepository.class));
  }

  @Test
  void testJdbcUsesDefaultTableName() {
    this.runner
        .withUserConfiguration(DataSourceConfiguration.class)
        .withPropertyValues("audit.repository.jdbc.table-name=")
        .run(context -> {
          assertThat(context.getBean(AuditRepositoryProperties.class).getJdbc().getTableName())
              .isEqualTo(DefaultJdbcAuditEventDao.DEFAULT_TABLE_NAME);

          final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
          repository.add(new AuditEvent("alice", "login", Map.of()));
          assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
        });
  }

  @Test
  void testMongoUsesDefaultCollection() {
    this.runner
        .withBean(org.springframework.data.mongodb.core.MongoTemplate.class,
            () -> mock(org.springframework.data.mongodb.core.MongoTemplate.class))
        .withPropertyValues("audit.repository.mongo.collection=")
        .run(context -> {
          assertThat(context).hasSingleBean(AuditEventRepository.class);
          assertThat(context.getBean(AuditRepositoryProperties.class).getMongo().getCollection())
              .isEqualTo(DefaultMongoAuditEventDao.DEFAULT_COLLECTION_NAME);
        });
  }

  @Test
  void testRedisUsesDefaultKey() {
    this.runner
        .withBean("stringRedisTemplate", org.springframework.data.redis.core.StringRedisTemplate.class,
            () -> mock(org.springframework.data.redis.core.StringRedisTemplate.class))
        .withPropertyValues("audit.repository.redis.key=")
        .run(context -> {
          assertThat(context).hasSingleBean(AuditEventRepository.class);
          assertThat(context.getBean(AuditRepositoryProperties.class).getRedis().getKey())
              .isEqualTo(AuditRepositoryProperties.Redis.DEFAULT_KEY);
        });
  }

  @Test
  void testInMemoryEnabledWithoutCapacity() {
    this.runner.withPropertyValues("audit.repository.in-memory.enabled=true").run(context -> {
      assertThat(context.getBean(AuditRepositoryProperties.class).getInMemory().getCapacity()).isNull();

      final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
      repository.add(new AuditEvent("alice", "login", Map.of()));
      assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
    });
  }

  @Test
  void testRedisEnabledWithoutKey() {
    this.runner
        .withBean("stringRedisTemplate", org.springframework.data.redis.core.StringRedisTemplate.class,
            () -> mock(org.springframework.data.redis.core.StringRedisTemplate.class))
        .withPropertyValues("audit.repository.redis.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(AuditEventRepository.class);
          assertThat(context.getBean(AuditRepositoryProperties.class).getRedis().getKey())
              .isEqualTo(AuditRepositoryProperties.Redis.DEFAULT_KEY);
        });
  }

  @Test
  void testDisabledFileRepositoryIsNotSetUp(@TempDir final Path directory) {
    final Path logFile = directory.resolve("audit.log");
    this.runner.withPropertyValues(
            "audit.repository.file.enabled=false",
            "audit.repository.file.log-file=" + logFile)
        .run(context -> {
          context.getBean(AuditEventRepository.class).add(new AuditEvent("alice", "login", Map.of()));
          assertThat(logFile).doesNotExist();
        });
  }

  @Test
  void testDisabledRepositorySettingsAreNotChecked() {
    this.runner.withPropertyValues(
            "audit.repository.file.enabled=false",
            "audit.repository.syslog.enabled=false",
            "audit.repository.syslog.transport=sctp",
            "audit.repository.in-memory.enabled=false",
            "audit.repository.in-memory.capacity=0")
        .run(context -> assertThat(context).hasSingleBean(AuditEventRepository.class));
  }

  @Test
  void testDisabledJdbcRepositoryIsNotSetUpEvenWithCustomDaoBean() {
    this.runner
        .withBean(JdbcAuditEventDao.class, RecordingDao::new)
        .withPropertyValues("audit.repository.jdbc.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(AuditEventRepository.class);
          context.getBean(AuditEventRepository.class).add(new AuditEvent("alice", "login", Map.of()));
          assertThat(((RecordingDao) context.getBean(JdbcAuditEventDao.class)).saved).isEmpty();
        });
  }

  @Test
  void testInMemoryIsNotConfiguredWhenNoCapacityIsAssigned() {
    this.runner.withPropertyValues("audit.repository.in-memory.capacity=").run(context -> {
      // The capacity is the only in-memory setting, so an empty value leaves the repository unconfigured. The
      // fallback in-memory repository (with its default capacity) is added anyway, so events can be read.
      assertThat(context.getBean(AuditRepositoryProperties.class).getInMemory()).isNull();

      final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
      repository.add(new AuditEvent("alice", "login", Map.of()));
      assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
    });
  }

  @Test
  void testSyslogRequiresHost() {
    this.runner.withPropertyValues("audit.repository.syslog.transport=udp").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context).getFailure().hasMessageContaining("audit.repository.syslog.host");
    });
  }

  @Test
  void testRepositoriesThatAreNotConfiguredAreNotSetUp(@TempDir final Path directory) {
    final Path logFile = directory.resolve("audit.log");
    this.runner.run(context -> {
      assertThat(context.getBean(AuditRepositoryProperties.class).getFile()).isNull();
      assertThat(context.getBean(AuditRepositoryProperties.class).getInMemory()).isNull();
      assertThat(context.getBean(AuditRepositoryProperties.class).getJdbc()).isNull();
      assertThat(context.getBean(AuditRepositoryProperties.class).getMongo()).isNull();
      assertThat(context.getBean(AuditRepositoryProperties.class).getRedis()).isNull();
      assertThat(context.getBean(AuditRepositoryProperties.class).getSyslog()).isNull();

      context.getBean(AuditEventRepository.class).add(new AuditEvent("alice", "login", Map.of()));
      assertThat(logFile).doesNotExist();
    });
  }

  @Test
  void testIncludeEventsFilter() {
    this.runner.withPropertyValues("audit.repository.include-events=login").run(context -> {
      final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
      repository.add(new AuditEvent("alice", "logout", Map.of()));
      repository.add(new AuditEvent("alice", "login", Map.of()));
      assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
    });
  }

  @Test
  void testJdbcRepository() {
    this.runner
        .withUserConfiguration(DataSourceConfiguration.class)
        .withPropertyValues("audit.repository.jdbc.table-name=audit_events")
        .run(context -> {
          final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
          repository.add(new AuditEvent("alice", "login", Map.of()));
          assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
        });
  }

  @Test
  void testJdbcUsesCustomDaoBean() {
    this.runner
        .withBean(JdbcAuditEventDao.class, RecordingDao::new)
        .run(context -> {
          final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
          repository.add(new AuditEvent("alice", "login", Map.of()));
          assertThat(repository.find(null, null, null)).extracting(AuditEvent::getType).containsExactly("login");
        });
  }

  @Test
  void testJdbcRequiresDataSource() {
    this.runner.withPropertyValues("audit.repository.jdbc.table-name=audit_events").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context).getFailure().hasMessageContaining("audit.repository.jdbc");
    });
  }

  @Test
  void testMongoRepositoryWiring() {
    this.runner
        .withBean(org.springframework.data.mongodb.core.MongoTemplate.class,
            () -> mock(org.springframework.data.mongodb.core.MongoTemplate.class))
        .withPropertyValues("audit.repository.mongo.collection=audit")
        .run(context -> assertThat(context).hasSingleBean(AuditEventRepository.class));
  }

  @Test
  void testRedisRepositoryWiring() {
    this.runner
        .withBean("stringRedisTemplate", org.springframework.data.redis.core.StringRedisTemplate.class,
            () -> mock(org.springframework.data.redis.core.StringRedisTemplate.class))
        .withPropertyValues("audit.repository.redis.key=my:audit")
        .run(context -> assertThat(context).hasSingleBean(AuditEventRepository.class));
  }

  @Test
  void testSyslogUsesProvidedBean() {
    try (final CapturingSender sender = new CapturingSender()) {
      this.runner
          .withBean(SyslogMessageSender.class, () -> sender)
          .run(context -> {
            final AuditEventRepository repository = context.getBean(AuditEventRepository.class);
            repository.add(new AuditEvent("alice", "login", Map.of()));
            assertThat(sender.messages).hasSize(1);
            assertThat(sender.messages.getFirst().toString()).contains("\"type\":\"login\"");
          });
    }
  }

  @Test
  void testSyslogBuiltFromProperties() {
    this.runner.withPropertyValues(
            "audit.repository.syslog.host=127.0.0.1",
            "audit.repository.syslog.transport=udp")
        .run(context -> assertThat(context).hasSingleBean(AuditEventRepository.class));
  }

  /**
   * Supplies an H2 data source with the audit table created.
   */
  @Configuration(proxyBeanMethods = false)
  static class DataSourceConfiguration {

    @Bean
    DataSource dataSource() {
      final DataSource dataSource = new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .generateUniqueName(true)
          .build();
      new JdbcTemplate(dataSource).execute("""
          CREATE TABLE audit_events (
            id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            event_time       TIMESTAMP NOT NULL,
            principal        VARCHAR(255),
            event_type       VARCHAR(255) NOT NULL,
            application_name  VARCHAR(255),
            correlation_id   VARCHAR(255),
            event_data       CLOB NOT NULL
          )""");
      return dataSource;
    }
  }

  /**
   * A {@link JdbcAuditEventDao} that records events in memory - stands in for a custom-schema DAO.
   */
  static class RecordingDao implements JdbcAuditEventDao {

    private final List<AuditEvent> saved = new ArrayList<>();

    @Override
    public void save(final @NonNull AuditEvent event) {
      this.saved.add(event);
    }

    @Override
    public @NonNull List<AuditEvent> find(final String principal, final Instant after, final String type) {
      return List.copyOf(this.saved);
    }

    @Override
    public @NonNull List<AuditEvent> findRecent(final int limit) {
      return List.copyOf(this.saved);
    }
  }

  /**
   * A {@link SyslogMessageSender} that captures the messages sent to it.
   */
  static class CapturingSender implements SyslogMessageSender {

    private final List<CharSequence> messages = new ArrayList<>();

    @Override
    public void sendMessage(final CharSequence message) {
      this.messages.add(message);
    }

    @Override
    public void sendMessage(final @NonNull SyslogMessage message) {
    }

    @Override
    public void sendMessage(final CharArrayWriter message) {
    }

    @Override
    public void close() {
    }
  }
}
