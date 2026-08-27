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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import se.swedenconnect.spring.audit.repository.DefaultJdbcAuditEventDao;
import se.swedenconnect.spring.audit.repository.DefaultMongoAuditEventDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Configuration properties for setting up an audit event repository.
 * <p>
 * A repository is set up if, and only if, its settings are configured. For example, audit logging to a file is set up
 * by assigning audit.repository.file.log-file. A repository whose settings all have defaults is set up by assigning
 * its enabled setting, for example audit.repository.redis.enabled. Any number of repositories may be configured at the
 * same time - each audit event is then written to all of them.
 * </p>
 * <p>
 * The enabled setting defaults to true, meaning that it only has to be assigned if no other setting is given, or if a
 * configured repository should be turned off (enabled = false). A repository that is turned off is not set up, and its
 * remaining settings are not checked.
 * </p>
 *
 * @author Martin Lindström
 */
@ConfigurationProperties(prefix = "audit.repository")
public class AuditRepositoryProperties implements InitializingBean {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(AuditRepositoryProperties.class);

  /** Settings for audit logging to an in-memory repository. Not assigned means no in-memory audit logging. */
  private @Nullable InMemory inMemory;

  /** Settings for audit logging to a file. Not assigned means no audit logging to a file. */
  private @Nullable File file;

  /** Settings for audit logging to a relational database. Not assigned means no audit logging to a database. */
  private @Nullable Jdbc jdbc;

  /** Settings for audit logging to MongoDB. Not assigned means no audit logging to MongoDB. */
  private @Nullable Mongo mongo;

  /** Settings for audit logging to Redis. Not assigned means no audit logging to Redis. */
  private @Nullable Redis redis;

  /** Settings for audit logging to syslog. Not assigned means no audit logging to syslog. */
  private @Nullable Syslog syslog;

  /**
   * The event types to log. If the list is non-empty, only events of these types are logged (except for those listed
   * under exclude-events). The default is to log events of all types.
   */
  private final @NonNull List<String> includeEvents = new ArrayList<>();

  /** The event types not to log. The default is to log events of all types. */
  private final @NonNull List<String> excludeEvents = new ArrayList<>();

  /**
   * Whether a failure to write an audit event should be reported by throwing an exception. If not assigned, the
   * repository default applies, which is to throw.
   */
  private @Nullable Boolean throwOnWriteFail;

  /**
   * Validates the settings of all configured repositories. The settings of a repository that is not enabled are not
   * checked.
   *
   * @throws IllegalArgumentException if the settings of an enabled repository are invalid or incomplete
   */
  @Override
  public void afterPropertiesSet() throws IllegalArgumentException {
    Stream.of(this.inMemory, this.file, this.jdbc, this.mongo, this.redis, this.syslog)
        .filter(Objects::nonNull)
        .forEach(RepositorySettings::afterPropertiesSet);
  }

  /**
   * Gets the in-memory repository settings.
   *
   * @return the in-memory settings, or {@code null} if no in-memory repository has been configured
   */
  public @Nullable InMemory getInMemory() {
    return this.inMemory;
  }

  /**
   * Assigns the in-memory repository settings.
   *
   * @param inMemory the in-memory settings
   */
  public void setInMemory(final @Nullable InMemory inMemory) {
    this.inMemory = inMemory;
  }

  /**
   * Gets the file repository settings.
   *
   * @return the file settings, or {@code null} if no file repository has been configured
   */
  public @Nullable File getFile() {
    return this.file;
  }

  /**
   * Assigns the file repository settings.
   *
   * @param file the file settings
   */
  public void setFile(final @Nullable File file) {
    this.file = file;
  }

  /**
   * Gets the JDBC repository settings.
   *
   * @return the JDBC settings, or {@code null} if no JDBC repository has been configured
   */
  public @Nullable Jdbc getJdbc() {
    return this.jdbc;
  }

  /**
   * Assigns the JDBC repository settings.
   *
   * @param jdbc the JDBC settings
   */
  public void setJdbc(final @Nullable Jdbc jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Gets the MongoDB repository settings.
   *
   * @return the MongoDB settings, or {@code null} if no MongoDB repository has been configured
   */
  public @Nullable Mongo getMongo() {
    return this.mongo;
  }

  /**
   * Assigns the MongoDB repository settings.
   *
   * @param mongo the MongoDB settings
   */
  public void setMongo(final @Nullable Mongo mongo) {
    this.mongo = mongo;
  }

  /**
   * Gets the Redis repository settings.
   *
   * @return the Redis settings, or {@code null} if no Redis repository has been configured
   */
  public @Nullable Redis getRedis() {
    return this.redis;
  }

  /**
   * Assigns the Redis repository settings.
   *
   * @param redis the Redis settings
   */
  public void setRedis(final @Nullable Redis redis) {
    this.redis = redis;
  }

  /**
   * Gets the syslog repository settings.
   *
   * @return the syslog settings, or {@code null} if no syslog repository has been configured
   */
  public @Nullable Syslog getSyslog() {
    return this.syslog;
  }

  /**
   * Assigns the syslog repository settings.
   *
   * @param syslog the syslog settings
   */
  public void setSyslog(final @Nullable Syslog syslog) {
    this.syslog = syslog;
  }

  /**
   * Gets the modifiable list of event types to log. If the list is non-empty, only events of these types are logged
   * (except for those given by {@link #getExcludeEvents()}). An empty list means that all event types are logged.
   *
   * @return the event types to log
   */
  public @NonNull List<String> getIncludeEvents() {
    return this.includeEvents;
  }

  /**
   * Gets the modifiable list of event types not to log.
   *
   * @return the event types not to log
   */
  public @NonNull List<String> getExcludeEvents() {
    return this.excludeEvents;
  }

  /**
   * Tells whether a failure to write an audit event should be reported by throwing an exception.
   *
   * @return the flag, or {@code null} if not assigned (meaning that the repository default applies)
   */
  public @Nullable Boolean getThrowOnWriteFail() {
    return this.throwOnWriteFail;
  }

  /**
   * Assigns whether a failure to write an audit event should be reported by throwing an exception. If not assigned, the
   * repository default applies, which is to throw.
   *
   * @param throwOnWriteFail the flag
   */
  public void setThrowOnWriteFail(final @Nullable Boolean throwOnWriteFail) {
    this.throwOnWriteFail = throwOnWriteFail;
  }

  /**
   * Base interface for the settings of a repository.
   */
  public interface RepositorySettings extends InitializingBean {

    /**
     * Tells whether the repository is enabled. The default is {@code true}, i.e., a repository is enabled by
     * configuring any of its settings.
     *
     * @return whether the repository is enabled
     */
    boolean isEnabled();

    /**
     * Validates the settings, and applies default values for settings that have not been assigned. If the repository
     * is not {@link #isEnabled() enabled}, nothing is checked.
     *
     * @throws IllegalArgumentException if the settings are invalid or incomplete
     */
    @Override
    void afterPropertiesSet() throws IllegalArgumentException;
  }

  /**
   * Settings for audit logging to an in-memory repository.
   * <p>
   * An in-memory repository is always set up if no other configured repository can serve queries, so that audit events
   * can be read (for example via the actuator auditevents endpoint). Therefore, these settings only need to be
   * assigned if the repository should hold another number of events than the default.
   * </p>
   */
  public static class InMemory implements RepositorySettings {

    /**
     * Whether audit logging to an in-memory repository is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /**
     * The number of events that the repository should hold. If not assigned, the default capacity of the underlying
     * repository is used.
     */
    private @Nullable Integer capacity;

    /**
     * Validates the settings.
     *
     * @throws IllegalArgumentException if the capacity is invalid
     */
    @Override
    public void afterPropertiesSet() throws IllegalArgumentException {
      if (!this.enabled) {
        return;
      }
      if (this.capacity == null) {
        log.info("audit.repository.in-memory.capacity is not assigned - using the default capacity");
      }
      else {
        Assert.isTrue(this.capacity > 0, "audit.repository.in-memory.capacity must be greater than 0");
      }
    }

    /**
     * Tells whether audit logging to an in-memory repository is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to an in-memory repository is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the number of events that the repository should hold.
     *
     * @return the capacity, or {@code null} if not assigned (meaning that the default capacity is used)
     */
    public @Nullable Integer getCapacity() {
      return this.capacity;
    }

    /**
     * Assigns the number of events that the repository should hold. If not assigned, the default capacity of the
     * underlying repository is used.
     *
     * @param capacity the capacity
     */
    public void setCapacity(final @Nullable Integer capacity) {
      this.capacity = capacity;
    }
  }

  /**
   * Settings for audit logging to a file.
   * <p>
   * The file is rolled per date (UTC) - when the first event of a new day is written, the current file is renamed to
   * name-yyyyMMdd.ext and a fresh file is started. Note that a file repository is write-only, i.e., it can not serve
   * queries for audit events.
   * </p>
   */
  public static class File implements RepositorySettings {

    /**
     * Whether audit logging to a file is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /** The complete path to the file where audit events are written. Required. */
    private @Nullable String logFile;

    /**
     * Validates the settings.
     *
     * @throws IllegalArgumentException if the log file is missing
     */
    @Override
    public void afterPropertiesSet() throws IllegalArgumentException {
      if (!this.enabled) {
        return;
      }
      Assert.hasText(this.logFile, "audit.repository.file.log-file must be assigned");
    }

    /**
     * Tells whether audit logging to a file is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to a file is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the complete path to the file where audit events are written.
     *
     * @return the log file path
     */
    public @Nullable String getLogFile() {
      return this.logFile;
    }

    /**
     * Assigns the complete path to the file where audit events are written.
     *
     * @param logFile the log file path
     */
    public void setLogFile(final @Nullable String logFile) {
      this.logFile = logFile;
    }
  }

  /**
   * Settings for audit logging to a relational database. Requires a javax.sql.DataSource bean and spring-jdbc on the
   * classpath.
   * <p>
   * If the application supplies a JdbcAuditEventDao bean, audit logging to a relational database is set up based on
   * that bean, and these settings should not be assigned.
   * </p>
   */
  public static class Jdbc implements RepositorySettings {

    /**
     * Whether audit logging to a relational database is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /**
     * The name of the table holding the audit events. If not assigned, audit_events is used.
     */
    private @Nullable String tableName;

    /**
     * Applies the default table name if none has been assigned.
     */
    @Override
    public void afterPropertiesSet() {
      if (!this.enabled) {
        return;
      }
      if (!StringUtils.hasText(this.tableName)) {
        log.info("audit.repository.jdbc.table-name is not assigned - using '{}'",
            DefaultJdbcAuditEventDao.DEFAULT_TABLE_NAME);
        this.tableName = DefaultJdbcAuditEventDao.DEFAULT_TABLE_NAME;
      }
    }

    /**
     * Tells whether audit logging to a relational database is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to a relational database is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the name of the table holding the audit events.
     *
     * @return the table name, or {@code null} if not assigned (meaning that the default table name is used)
     */
    public @Nullable String getTableName() {
      return this.tableName;
    }

    /**
     * Assigns the name of the table holding the audit events. If not assigned, the default table name is used.
     *
     * @param tableName the table name
     */
    public void setTableName(final @Nullable String tableName) {
      this.tableName = tableName;
    }
  }

  /**
   * Settings for audit logging to MongoDB. Requires a MongoTemplate bean and spring-data-mongodb on the classpath.
   * <p>
   * If the application supplies a MongoAuditEventDao bean, audit logging to MongoDB is set up based on that bean, and
   * these settings should not be assigned.
   * </p>
   */
  public static class Mongo implements RepositorySettings {

    /**
     * Whether audit logging to MongoDB is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /**
     * The name of the collection holding the audit events. If not assigned, audit_events is used.
     */
    private @Nullable String collection;

    /**
     * Applies the default collection name if none has been assigned.
     */
    @Override
    public void afterPropertiesSet() {
      if (!this.enabled) {
        return;
      }
      if (!StringUtils.hasText(this.collection)) {
        log.info("audit.repository.mongo.collection is not assigned - using '{}'",
            DefaultMongoAuditEventDao.DEFAULT_COLLECTION_NAME);
        this.collection = DefaultMongoAuditEventDao.DEFAULT_COLLECTION_NAME;
      }
    }

    /**
     * Tells whether audit logging to MongoDB is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to MongoDB is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the name of the collection holding the audit events.
     *
     * @return the collection name, or {@code null} if not assigned (meaning that the default collection name is used)
     */
    public @Nullable String getCollection() {
      return this.collection;
    }

    /**
     * Assigns the name of the collection holding the audit events. If not assigned, the default collection name is
     * used.
     *
     * @param collection the collection name
     */
    public void setCollection(final @Nullable String collection) {
      this.collection = collection;
    }
  }

  /**
   * Settings for audit logging to Redis. Requires spring-data-redis on the classpath and that the Redis connection is
   * configured for the application (spring.data.redis.*), so that a StringRedisTemplate bean is available.
   */
  public static class Redis implements RepositorySettings {

    /**
     * Whether audit logging to Redis is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /** The default Redis key. */
    public static final String DEFAULT_KEY = "audit:events";

    /**
     * The name of the Redis key (a sorted set) holding the audit events. If not assigned, audit:events is used.
     */
    private @Nullable String key;

    /**
     * Applies the default key if none has been assigned.
     */
    @Override
    public void afterPropertiesSet() {
      if (!this.enabled) {
        return;
      }
      if (!StringUtils.hasText(this.key)) {
        log.info("audit.repository.redis.key is not assigned - using '{}'", DEFAULT_KEY);
        this.key = DEFAULT_KEY;
      }
    }

    /**
     * Tells whether audit logging to Redis is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to Redis is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the name of the Redis key holding the audit events.
     *
     * @return the Redis key, or {@code null} if not assigned (meaning that {@value #DEFAULT_KEY} is used)
     */
    public @Nullable String getKey() {
      return this.key;
    }

    /**
     * Assigns the name of the Redis key holding the audit events. If not assigned, {@value #DEFAULT_KEY} is used.
     *
     * @param key the Redis key
     */
    public void setKey(final @Nullable String key) {
      this.key = key;
    }
  }

  /**
   * Settings for audit logging to syslog. Requires syslog-java-client on the classpath.
   * <p>
   * If the application supplies a SyslogMessageSender bean, audit logging to syslog is set up based on that bean, and
   * these settings should not be assigned.
   * </p>
   */
  public static class Syslog implements RepositorySettings {

    /**
     * Whether audit logging to syslog is enabled. The default is true, meaning that the repository is
     * enabled by configuring any of its settings. Assign false to turn the repository off without removing its settings.
     */
    private boolean enabled = true;

    /** The transport for sending syslog messages over UDP. */
    public static final String UDP_TRANSPORT = "udp";

    /** The transport for sending syslog messages over TCP. */
    public static final String TCP_TRANSPORT = "tcp";

    /** The default syslog server port. */
    public static final int DEFAULT_PORT = 514;

    /** The host name of the syslog server. Required. */
    private @Nullable String host;

    /** The port of the syslog server. If not assigned, 514 is used. */
    private @Nullable Integer port;

    /** The transport to use - udp or tcp. If not assigned, udp is used. */
    private @Nullable String transport;

    /** The syslog facility, for example LOCAL0. If not assigned, LOCAL0 is used. */
    private @Nullable String facility;

    /** The syslog severity, for example INFORMATIONAL. If not assigned, INFORMATIONAL is used. */
    private @Nullable String severity;

    /** The syslog message format - RFC_5424 or RFC_3164. If not assigned, RFC_5424 is used. */
    private @Nullable String messageFormat;

    /** The application name to report in the syslog messages. If not assigned, the Spring application name is used. */
    private @Nullable String appName;

    /**
     * Validates the settings.
     *
     * @throws IllegalArgumentException if the host is missing, or if the transport or port is invalid
     */
    @Override
    public void afterPropertiesSet() throws IllegalArgumentException {
      if (!this.enabled) {
        return;
      }
      Assert.hasText(this.host, "audit.repository.syslog.host must be assigned");

      final String transportValue = this.transport;
      if (StringUtils.hasText(transportValue)) {
        Assert.isTrue(UDP_TRANSPORT.equalsIgnoreCase(transportValue) || TCP_TRANSPORT.equalsIgnoreCase(transportValue),
            "Invalid audit.repository.syslog.transport '%s' - expected '%s' or '%s'"
                .formatted(transportValue, UDP_TRANSPORT, TCP_TRANSPORT));
      }
      final Integer portValue = this.port;
      if (portValue != null) {
        Assert.isTrue(portValue > 0 && portValue <= 65535,
            "audit.repository.syslog.port must be in the range 1-65535");
      }
    }

    /**
     * Tells whether audit logging to syslog is enabled.
     *
     * @return whether enabled
     */
    @Override
    public boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Assigns whether audit logging to syslog is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the host name of the syslog server.
     *
     * @return the host
     */
    public @Nullable String getHost() {
      return this.host;
    }

    /**
     * Assigns the host name of the syslog server.
     *
     * @param host the host
     */
    public void setHost(final @Nullable String host) {
      this.host = host;
    }

    /**
     * Gets the port of the syslog server.
     *
     * @return the port, or {@code null} if not assigned (meaning that {@value #DEFAULT_PORT} is used)
     */
    public @Nullable Integer getPort() {
      return this.port;
    }

    /**
     * Assigns the port of the syslog server. If not assigned, {@value #DEFAULT_PORT} is used.
     *
     * @param port the port
     */
    public void setPort(final @Nullable Integer port) {
      this.port = port;
    }

    /**
     * Gets the transport to use.
     *
     * @return the transport, or {@code null} if not assigned (meaning that udp is used)
     */
    public @Nullable String getTransport() {
      return this.transport;
    }

    /**
     * Assigns the transport to use - {@value #UDP_TRANSPORT} or {@value #TCP_TRANSPORT}. If not assigned,
     * {@value #UDP_TRANSPORT} is used.
     *
     * @param transport the transport
     */
    public void setTransport(final @Nullable String transport) {
      this.transport = transport;
    }

    /**
     * Gets the syslog facility.
     *
     * @return the facility, or {@code null} if not assigned (meaning that LOCAL0 is used)
     */
    public @Nullable String getFacility() {
      return this.facility;
    }

    /**
     * Assigns the syslog facility. If not assigned, LOCAL0 is used.
     *
     * @param facility the facility
     */
    public void setFacility(final @Nullable String facility) {
      this.facility = facility;
    }

    /**
     * Gets the syslog severity.
     *
     * @return the severity, or {@code null} if not assigned (meaning that INFORMATIONAL is used)
     */
    public @Nullable String getSeverity() {
      return this.severity;
    }

    /**
     * Assigns the syslog severity. If not assigned, INFORMATIONAL is used.
     *
     * @param severity the severity
     */
    public void setSeverity(final @Nullable String severity) {
      this.severity = severity;
    }

    /**
     * Gets the syslog message format.
     *
     * @return the message format, or {@code null} if not assigned (meaning that RFC_5424 is used)
     */
    public @Nullable String getMessageFormat() {
      return this.messageFormat;
    }

    /**
     * Assigns the syslog message format - RFC_5424 or RFC_3164. If not assigned, RFC_5424 is used.
     *
     * @param messageFormat the message format
     */
    public void setMessageFormat(final @Nullable String messageFormat) {
      this.messageFormat = messageFormat;
    }

    /**
     * Gets the application name to report in the syslog messages.
     *
     * @return the application name, or {@code null} if not assigned (meaning that the Spring application name is used)
     */
    public @Nullable String getAppName() {
      return this.appName;
    }

    /**
     * Assigns the application name to report in the syslog messages. If not assigned, the Spring application name is
     * used.
     *
     * @param appName the application name
     */
    public void setAppName(final @Nullable String appName) {
      this.appName = appName;
    }
  }

}
