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

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.sender.AbstractSyslogMessageSender;
import com.cloudbees.syslog.sender.SyslogMessageSender;
import com.cloudbees.syslog.sender.TcpSyslogMessageSender;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.autoconfigure.audit.AuditAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import se.swedenconnect.spring.audit.repository.AbstractAuditEventRepository;
import se.swedenconnect.spring.audit.repository.AuditEventDao;
import se.swedenconnect.spring.audit.repository.AuditEventMapper;
import se.swedenconnect.spring.audit.repository.DatabaseAuditEventRepository;
import se.swedenconnect.spring.audit.repository.DefaultJdbcAuditEventDao;
import se.swedenconnect.spring.audit.repository.DefaultMongoAuditEventDao;
import se.swedenconnect.spring.audit.repository.DelegatingAuditEventRepository;
import se.swedenconnect.spring.audit.repository.ExtendedAuditEventRepository;
import se.swedenconnect.spring.audit.repository.FileBasedAuditEventRepository;
import se.swedenconnect.spring.audit.repository.InMemoryAuditEventRepository;
import se.swedenconnect.spring.audit.repository.JdbcAuditEventDao;
import se.swedenconnect.spring.audit.repository.JsonAuditEventMapper;
import se.swedenconnect.spring.audit.repository.MongoAuditEventDao;
import se.swedenconnect.spring.audit.repository.RedisAuditEventRepository;
import se.swedenconnect.spring.audit.repository.SyslogAuditEventRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-configuration that sets up an {@link AuditEventRepository} bean based on the {@code audit.repository.*}
 * {@link AuditRepositoryProperties properties}.
 * <p>
 * A repository is set up if, and only if, its {@code audit.repository.*} settings are configured and not turned off
 * (using its {@code enabled} setting), or, for the JDBC, MongoDB and syslog repositories, if the application supplies a
 * bean that sets the repository up. The configured
 * repositories are assembled into a {@link DelegatingAuditEventRepository} (which applies the configured
 * include/exclude filter and write-failure policy). If no queryable repository is configured, an in-memory repository
 * is added so that audit events can still be read (for example via the actuator {@code auditevents} endpoint). The
 * whole configuration backs off if the application already declares an {@link AuditEventRepository} bean.
 * </p>
 *
 * @author Martin Lindström
 */
@AutoConfiguration(before = AuditAutoConfiguration.class)
@ConditionalOnMissingBean(AuditEventRepository.class)
@EnableConfigurationProperties(AuditRepositoryProperties.class)
public class AuditRepositoryAutoConfiguration {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(AuditRepositoryAutoConfiguration.class);

  /**
   * Creates an {@link AuditEventMapper} bean, unless one has already been provided.
   *
   * @param objectMapper the JSON object mapper to use (if available)
   * @return an {@link AuditEventMapper}
   */
  @Bean
  @ConditionalOnMissingBean
  AuditEventMapper auditEventMapper(final ObjectProvider<ObjectMapper> objectMapper) {
    return new JsonAuditEventMapper(objectMapper.getIfAvailable(() -> JsonMapper.builder().build()));
  }

  /**
   * Assembles the {@link AuditEventRepository} bean from the configured repositories.
   * <p>
   * The order of the repositories matters, since {@link DelegatingAuditEventRepository} answers a query from the first
   * delegate that returns a result. Therefore, the in-memory repository is always placed last: it is a bounded buffer
   * holding only the most recent events, so if it were consulted first, a configured durable store (JDBC, MongoDB or
   * Redis) would never be queried, and only the tail of the audit log would be visible.
   * </p>
   * <p>
   * If no configured repository can serve queries, an in-memory repository is appended so that audit events can still
   * be read. A configured in-memory repository already satisfies that requirement, so at most one in-memory repository
   * is created.
   * </p>
   *
   * @param properties the audit repository properties
   * @param auditEventMapper the event mapper
   * @param jdbcSupplier a supplier for a JDBC repository (present only if JDBC is available and configured)
   * @param mongoSupplier a supplier for a MongoDB repository (present only if MongoDB is available and configured)
   * @param redisSupplier a supplier for a Redis repository (present only if Redis is available and configured)
   * @param syslogSupplier a supplier for a syslog repository (present only if syslog is available and configured)
   * @return an {@link AuditEventRepository}
   * @throws IOException if the file repository can not be set up
   */
  @Bean
  AuditEventRepository auditEventRepository(final AuditRepositoryProperties properties,
      final AuditEventMapper auditEventMapper,
      final ObjectProvider<JdbcAuditEventRepositorySupplier> jdbcSupplier,
      final ObjectProvider<MongoAuditEventRepositorySupplier> mongoSupplier,
      final ObjectProvider<RedisAuditEventRepositorySupplier> redisSupplier,
      final ObjectProvider<SyslogAuditEventRepositorySupplier> syslogSupplier) throws IOException {

    final List<AuditEventRepository> repositories = new ArrayList<>();

    // The settings of the configured repositories have been validated and defaulted - see
    // AuditRepositoryProperties#afterPropertiesSet().
    //
    if (isEnabled(properties.getFile())) {
      repositories.add(new FileBasedAuditEventRepository(properties.getFile().getLogFile(), auditEventMapper));
    }
    addOptional(repositories, isEnabled(properties.getJdbc()), jdbcSupplier, "jdbc",
        "spring-jdbc on the classpath");
    addOptional(repositories, isEnabled(properties.getMongo()), mongoSupplier, "mongo",
        "a MongoTemplate bean and spring-data-mongodb on the classpath");
    addOptional(repositories, isEnabled(properties.getRedis()), redisSupplier, "redis",
        "a StringRedisTemplate bean and spring-data-redis on the classpath");
    addOptional(repositories, isEnabled(properties.getSyslog()), syslogSupplier, "syslog",
        "syslog-java-client on the classpath");

    // The in-memory repository is added last, so that a durable store is always consulted before the bounded
    // in-memory buffer - see the ordering note in the method documentation.
    //
    if (isEnabled(properties.getInMemory())) {
      repositories.add(properties.getInMemory().getCapacity() != null
          ? new InMemoryAuditEventRepository(properties.getInMemory().getCapacity())
          : new InMemoryAuditEventRepository());
    }

    // Make sure at least one repository can serve queries, so audit events can be read. A configured in-memory
    // repository already satisfies this, so at most one in-memory repository is ever created.
    final boolean queryable = repositories.stream()
        .anyMatch(r -> r instanceof final ExtendedAuditEventRepository e && e.supportsFind());
    if (!queryable) {
      repositories.add(new InMemoryAuditEventRepository());
      log.info("No queryable audit repository configured - added an in-memory repository so events can be read");
    }

    final DelegatingAuditEventRepository repository = new DelegatingAuditEventRepository(repositories,
        AbstractAuditEventRepository.inclusionExclusionPredicate(
            properties.getIncludeEvents(), properties.getExcludeEvents()));
    if (properties.getThrowOnWriteFail() != null) {
      repository.setThrowOnWriteFail(properties.getThrowOnWriteFail());
    }
    log.info("Configured audit event repository delegating to {} underlying repositor{}",
        repositories.size(), repositories.size() == 1 ? "y" : "ies");
    return repository;
  }

  /**
   * Tells whether a repository has been configured and enabled.
   *
   * @param settings the repository settings, or {@code null} if the repository has not been configured
   * @return {@code true} if the repository has been configured and is enabled, and {@code false} otherwise
   */
  private static boolean isEnabled(final AuditRepositoryProperties.@Nullable RepositorySettings settings) {
    return settings != null && settings.isEnabled();
  }

  /**
   * Adds the repository produced by an optional supplier. A supplier is present if the repository has been configured,
   * or if the application has supplied a bean that sets the repository up. If the repository has been configured, but
   * no supplier is available, its requirements are not met, and an exception is thrown.
   *
   * @param repositories the list to add to
   * @param configured whether the repository has been configured
   * @param supplier the optional supplier
   * @param name the repository name (for messages)
   * @param requirement a description of what the repository requires (for messages)
   */
  private static void addOptional(final List<AuditEventRepository> repositories, final boolean configured,
      final ObjectProvider<? extends AuditEventRepositorySupplier> supplier, final String name,
      final String requirement) {
    final AuditEventRepositorySupplier resolved = supplier.getIfAvailable();
    if (resolved != null) {
      repositories.add(resolved.get());
      return;
    }
    if (configured) {
      throw new BeanCreationException(
          "audit.repository.%s is configured, but this requires %s".formatted(name, requirement));
    }
  }

  /**
   * A supplier that creates an {@link AuditEventRepository}. Used to defer the reference to optional-dependency
   * repository classes until they are known to be available.
   */
  @FunctionalInterface
  interface AuditEventRepositorySupplier {

    /**
     * Creates the repository.
     *
     * @return an {@link AuditEventRepository}
     */
    AuditEventRepository get();
  }

  /**
   * Condition that matches if a repository has been configured, i.e., if any setting under its configuration
   * properties prefix has been assigned, or if the application has supplied a bean that sets the repository up.
   */
  abstract static class OnRepositoryConfiguredCondition implements Condition {

    /** The configuration properties prefix for the repository. */
    private final String prefix;

    /** The type of an application supplied bean that also sets the repository up, or {@code null} if there is none. */
    private final @Nullable Class<?> activatingBeanType;

    /**
     * Constructor.
     *
     * @param prefix the configuration properties prefix for the repository
     * @param activatingBeanType the type of an application supplied bean that also sets the repository up, or
     *     {@code null} if there is none
     */
    protected OnRepositoryConfiguredCondition(final String prefix, final @Nullable Class<?> activatingBeanType) {
      this.prefix = prefix;
      this.activatingBeanType = activatingBeanType;
    }

    /** {@inheritDoc} */
    @Override
    public boolean matches(final @NonNull ConditionContext context, final @NonNull AnnotatedTypeMetadata metadata) {
      final Binder binder = Binder.get(context.getEnvironment());
      if (!binder.bind(this.prefix + ".enabled", Boolean.class).orElse(true)) {
        return false;
      }
      if (binder.bind(this.prefix, Bindable.mapOf(String.class, String.class)).isBound()) {
        return true;
      }
      return this.activatingBeanType != null && context.getBeanFactory() != null
          && context.getBeanFactory().getBeanNamesForType(this.activatingBeanType, true, false).length > 0;
    }
  }

  /** Matches if audit logging to a relational database has been configured. */
  static class OnJdbcConfigured extends OnRepositoryConfiguredCondition {

    /** Constructor. */
    OnJdbcConfigured() {
      super("audit.repository.jdbc", JdbcAuditEventDao.class);
    }
  }

  /** Matches if audit logging to MongoDB has been configured. */
  static class OnMongoConfigured extends OnRepositoryConfiguredCondition {

    /** Constructor. */
    OnMongoConfigured() {
      super("audit.repository.mongo", MongoAuditEventDao.class);
    }
  }

  /** Matches if audit logging to Redis has been configured. */
  static class OnRedisConfigured extends OnRepositoryConfiguredCondition {

    /** Constructor. */
    OnRedisConfigured() {
      super("audit.repository.redis", null);
    }
  }

  /** Matches if audit logging to syslog has been configured. */
  static class OnSyslogConfigured extends OnRepositoryConfiguredCondition {

    /** Constructor. */
    OnSyslogConfigured() {
      super("audit.repository.syslog", SyslogMessageSender.class);
    }
  }

  /** Marker for a JDBC repository supplier. */
  @FunctionalInterface
  interface JdbcAuditEventRepositorySupplier extends AuditEventRepositorySupplier {
  }

  /** Marker for a MongoDB repository supplier. */
  @FunctionalInterface
  interface MongoAuditEventRepositorySupplier extends AuditEventRepositorySupplier {
  }

  /** Marker for a Redis repository supplier. */
  @FunctionalInterface
  interface RedisAuditEventRepositorySupplier extends AuditEventRepositorySupplier {
  }

  /** Marker for a syslog repository supplier. */
  @FunctionalInterface
  interface SyslogAuditEventRepositorySupplier extends AuditEventRepositorySupplier {
  }

  /**
   * Configuration that contributes a JDBC repository supplier when {@code spring-jdbc} is available and JDBC auditing
   * has been configured. If the application provides its own {@link JdbcAuditEventDao} bean (for example for a
   * non-default schema) it is used; otherwise a {@link DefaultJdbcAuditEventDao} is built from a {@link DataSource} and
   * the configured table name.
   */
  @ConditionalOnClass(JdbcTemplate.class)
  @Conditional(OnJdbcConfigured.class)
  static class JdbcRepositoryConfiguration {

    /**
     * Creates a JDBC repository supplier.
     *
     * @param auditEventDao a provider for an application-supplied {@link JdbcAuditEventDao} bean
     * @param dataSource a provider for a {@link DataSource} bean (used when no {@link JdbcAuditEventDao} is supplied)
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @return a {@link JdbcAuditEventRepositorySupplier}
     */
    @Bean
    JdbcAuditEventRepositorySupplier jdbcAuditEventRepositorySupplier(
        final ObjectProvider<JdbcAuditEventDao> auditEventDao, final ObjectProvider<DataSource> dataSource,
        final AuditEventMapper auditEventMapper, final AuditRepositoryProperties properties) {
      final AuditEventDao dao = resolveDao(auditEventDao, dataSource, auditEventMapper, properties);
      return () -> new DatabaseAuditEventRepository(dao);
    }

    /**
     * Resolves the {@link AuditEventDao} to use - an application-supplied {@link JdbcAuditEventDao} bean if present,
     * otherwise a {@link DefaultJdbcAuditEventDao} built from a {@link DataSource}.
     *
     * @param auditEventDao a provider for an application-supplied DAO
     * @param dataSource a provider for a data source
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @return an {@link AuditEventDao}
     */
    private static AuditEventDao resolveDao(final ObjectProvider<JdbcAuditEventDao> auditEventDao,
        final ObjectProvider<DataSource> dataSource, final AuditEventMapper auditEventMapper,
        final AuditRepositoryProperties properties) {

      final JdbcAuditEventDao providedDao = auditEventDao.getIfAvailable();
      if (providedDao != null) {
        if (isEnabled(properties.getJdbc())) {
          log.warn("A JdbcAuditEventDao bean is present - the audit.repository.jdbc settings are ignored");
        }
        return providedDao;
      }
      final DataSource resolvedDataSource = dataSource.getIfAvailable();
      if (resolvedDataSource == null) {
        throw new BeanCreationException("audit.repository.jdbc is configured, but requires either a "
            + "JdbcAuditEventDao bean or a javax.sql.DataSource bean");
      }
      // The table name is assigned, or defaulted - see AuditRepositoryProperties.Jdbc#afterPropertiesSet().
      return new DefaultJdbcAuditEventDao(
          new JdbcTemplate(resolvedDataSource), auditEventMapper, properties.getJdbc().getTableName());
    }
  }

  /**
   * Configuration that contributes a MongoDB repository supplier when {@code spring-data-mongodb} is available and
   * MongoDB auditing has been configured. If the application provides its own {@link MongoAuditEventDao} bean (for example for a
   * non-default schema) it is used; otherwise a {@link DefaultMongoAuditEventDao} is built from a {@link MongoTemplate}
   * and the configured collection name.
   */
  @ConditionalOnClass(MongoTemplate.class)
  @Conditional(OnMongoConfigured.class)
  static class MongoRepositoryConfiguration {

    /**
     * Creates a MongoDB repository supplier.
     *
     * @param auditEventDao a provider for an application-supplied {@link MongoAuditEventDao} bean
     * @param mongoTemplate a provider for a {@link MongoTemplate} bean (used when no {@link MongoAuditEventDao} is
     *     supplied)
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @return a {@link MongoAuditEventRepositorySupplier}
     */
    @Bean
    MongoAuditEventRepositorySupplier mongoAuditEventRepositorySupplier(
        final ObjectProvider<MongoAuditEventDao> auditEventDao, final ObjectProvider<MongoTemplate> mongoTemplate,
        final AuditEventMapper auditEventMapper, final AuditRepositoryProperties properties) {
      final AuditEventDao dao = resolveDao(auditEventDao, mongoTemplate, auditEventMapper, properties);
      return () -> new DatabaseAuditEventRepository(dao);
    }

    /**
     * Resolves the {@link AuditEventDao} to use - an application-supplied {@link MongoAuditEventDao} bean if present,
     * otherwise a {@link DefaultMongoAuditEventDao} built from a {@link MongoTemplate}.
     *
     * @param auditEventDao a provider for an application-supplied DAO
     * @param mongoTemplate a provider for a Mongo template
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @return an {@link AuditEventDao}
     */
    private static AuditEventDao resolveDao(final ObjectProvider<MongoAuditEventDao> auditEventDao,
        final ObjectProvider<MongoTemplate> mongoTemplate, final AuditEventMapper auditEventMapper,
        final AuditRepositoryProperties properties) {

      final MongoAuditEventDao providedDao = auditEventDao.getIfAvailable();
      if (providedDao != null) {
        if (isEnabled(properties.getMongo())) {
          log.warn("A MongoAuditEventDao bean is present - the audit.repository.mongo settings are ignored");
        }
        return providedDao;
      }
      final MongoTemplate resolvedMongoTemplate = mongoTemplate.getIfAvailable();
      if (resolvedMongoTemplate == null) {
        throw new BeanCreationException("audit.repository.mongo is configured, but requires either a "
            + "MongoAuditEventDao bean or a MongoTemplate bean");
      }
      // The collection name is assigned, or defaulted - see AuditRepositoryProperties.Mongo#afterPropertiesSet().
      return new DefaultMongoAuditEventDao(
          resolvedMongoTemplate, auditEventMapper, properties.getMongo().getCollection());
    }
  }

  /**
   * Configuration that contributes a Redis repository supplier when {@code spring-data-redis} and a
   * {@link StringRedisTemplate} are available and Redis auditing has been configured.
   */
  @ConditionalOnClass(StringRedisTemplate.class)
  @Conditional(OnRedisConfigured.class)
  static class RedisRepositoryConfiguration {

    /**
     * Creates a Redis repository supplier.
     *
     * @param redisTemplate the Redis template
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @return a {@link RedisAuditEventRepositorySupplier}
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    RedisAuditEventRepositorySupplier redisAuditEventRepositorySupplier(final StringRedisTemplate redisTemplate,
        final AuditEventMapper auditEventMapper, final AuditRepositoryProperties properties) {
      // The key is assigned, or defaulted - see AuditRepositoryProperties.Redis#afterPropertiesSet().
      final String key = properties.getRedis().getKey();
      return () -> new RedisAuditEventRepository(redisTemplate, key, auditEventMapper);
    }
  }

  /**
   * Configuration that contributes a syslog repository supplier when {@code syslog-java-client} is available and syslog
   * auditing has been configured. If the application provides a {@link SyslogMessageSender} bean it is used; otherwise
   * a sender
   * is built from the properties.
   */
  @ConditionalOnClass(SyslogMessageSender.class)
  @Conditional(OnSyslogConfigured.class)
  static class SyslogRepositoryConfiguration {

    /**
     * Creates a syslog repository supplier.
     *
     * @param senderProvider a provider for an application-supplied {@link SyslogMessageSender} bean
     * @param auditEventMapper the event mapper
     * @param properties the audit repository properties
     * @param environment the Spring environment (for resolving the default application name)
     * @return a {@link SyslogAuditEventRepositorySupplier}
     */
    @Bean
    SyslogAuditEventRepositorySupplier syslogAuditEventRepositorySupplier(
        final ObjectProvider<SyslogMessageSender> senderProvider, final AuditEventMapper auditEventMapper,
        final AuditRepositoryProperties properties, final Environment environment) {

      final AuditRepositoryProperties.Syslog config = properties.getSyslog();
      final SyslogMessageSender providedSender = senderProvider.getIfAvailable();

      final SyslogMessageSender sender;
      if (providedSender != null) {
        if (isEnabled(config)) {
          log.warn("A SyslogMessageSender bean is present - the audit.repository.syslog settings are ignored");
        }
        sender = providedSender;
      }
      else {
        sender = buildSender(config, environment);
      }
      return () -> new SyslogAuditEventRepository(sender, auditEventMapper);
    }

    /**
     * Builds a {@link SyslogMessageSender} from the configuration properties.
     *
     * @param config the syslog properties
     * @param environment the Spring environment
     * @return a {@link SyslogMessageSender}
     */
    private static SyslogMessageSender buildSender(final AuditRepositoryProperties.@Nullable Syslog config,
        final Environment environment) {

      // The host is guaranteed to be assigned - see AuditRepositoryProperties.Syslog#afterPropertiesSet().
      Assert.notNull(config, "audit.repository.syslog must be configured, or a SyslogMessageSender bean provided");

      final String host = config.getHost();
      final int port =
          Optional.ofNullable(config.getPort()).orElse(AuditRepositoryProperties.Syslog.DEFAULT_PORT);
      final String transport = Optional.ofNullable(config.getTransport())
          .orElse(AuditRepositoryProperties.Syslog.UDP_TRANSPORT)
          .toLowerCase();

      final AbstractSyslogMessageSender sender;
      if (AuditRepositoryProperties.Syslog.TCP_TRANSPORT.equals(transport)) {
        final TcpSyslogMessageSender tcp = new TcpSyslogMessageSender();
        tcp.setSyslogServerHostname(host);
        tcp.setSyslogServerPort(port);
        sender = tcp;
      }
      else if (AuditRepositoryProperties.Syslog.UDP_TRANSPORT.equals(transport)) {
        final UdpSyslogMessageSender udp = new UdpSyslogMessageSender();
        udp.setSyslogServerHostname(host);
        udp.setSyslogServerPort(port);
        sender = udp;
      }
      else {
        throw new BeanCreationException(
            "Invalid audit.repository.syslog.transport '%s' - expected 'udp' or 'tcp'".formatted(config.getTransport()));
      }

      final String appName = Optional.ofNullable(config.getAppName())
          .orElseGet(() -> environment.getProperty("spring.application.name", "audit"));
      sender.setDefaultAppName(appName);
      sender.setDefaultFacility(
          parseEnum(Facility.class, config.getFacility(), Facility.LOCAL0, "audit.repository.syslog.facility"));
      sender.setDefaultSeverity(parseEnum(Severity.class, config.getSeverity(), Severity.INFORMATIONAL,
          "audit.repository.syslog.severity"));
      sender.setMessageFormat(parseEnum(MessageFormat.class, config.getMessageFormat(), MessageFormat.RFC_5424,
          "audit.repository.syslog.message-format"));
      return sender;
    }

    /**
     * Parses an enum value from a configuration string, falling back to a default when unset.
     *
     * @param type the enum type
     * @param value the configured value (may be {@code null})
     * @param defaultValue the default value
     * @param property the property name (for error messages)
     * @param <E> the enum type
     * @return the parsed enum value
     */
    private static <E extends Enum<E>> E parseEnum(final Class<E> type, final String value, final E defaultValue,
        final String property) {
      if (!StringUtils.hasText(value)) {
        return defaultValue;
      }
      try {
        return Enum.valueOf(type, value.toUpperCase());
      }
      catch (final IllegalArgumentException e) {
        throw new BeanCreationException("Invalid value '%s' for %s".formatted(value, property), e);
      }
    }
  }

}
