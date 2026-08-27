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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The default {@link AuditEventDao} implementation, backed by a
 * {@link org.springframework.jdbc.core.JdbcTemplate JdbcTemplate}.
 * <p>
 * Each audit event is stored as a single row. A set of flat columns ({@code event_time}, {@code principal},
 * {@code event_type}, {@code application_name}, {@code correlation_id}) is used for querying, while the complete event
 * is stored as JSON in the {@code event_data} column using an {@link AuditEventMapper}. On read, the event is
 * reconstructed from the {@code event_data} column, so no information is lost. Timestamps are stored in UTC.
 * </p>
 * <p>
 * The table name is configurable (defaulting to {@value #DEFAULT_TABLE_NAME}); the column names are fixed. See
 * {@code docs/jdbc.md} for the DDL. An application whose schema differs should implement {@link JdbcAuditEventDao}
 * directly instead of using this class.
 * </p>
 *
 * @author Martin Lindström
 */
public class DefaultJdbcAuditEventDao implements JdbcAuditEventDao {

  /** The default table name. */
  public static final String DEFAULT_TABLE_NAME = "audit_events";

  /** The template used for database access. */
  private final JdbcTemplate jdbcTemplate;

  /** The mapper used to (de)serialize the event to/from the {@code event_data} column. */
  private final AuditEventMapper eventMapper;

  /** Maps a row to an audit event by deserializing the {@code event_data} column. */
  private final RowMapper<AuditEvent> rowMapper;

  /** {@code INSERT} statement. */
  private final String insertSql;

  /** {@code SELECT} statement (without ordering and filtering). */
  private final String selectSql;

  /**
   * Constructor using the {@link #DEFAULT_TABLE_NAME default table name}.
   *
   * @param jdbcTemplate the template used for database access
   * @param eventMapper the mapper used to (de)serialize the event
   */
  public DefaultJdbcAuditEventDao(final @NonNull JdbcTemplate jdbcTemplate, final @NonNull AuditEventMapper eventMapper) {
    this(jdbcTemplate, eventMapper, DEFAULT_TABLE_NAME);
  }

  /**
   * Constructor.
   *
   * @param jdbcTemplate the template used for database access
   * @param eventMapper the mapper used to (de)serialize the event
   * @param tableName the name of the table holding the audit events (if {@code null} or blank, the
   *     {@link #DEFAULT_TABLE_NAME default} is used)
   */
  public DefaultJdbcAuditEventDao(final @NonNull JdbcTemplate jdbcTemplate, final @NonNull AuditEventMapper eventMapper,
      final @Nullable String tableName) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");
    final String table = Optional.ofNullable(tableName).filter(StringUtils::hasText).orElse(DEFAULT_TABLE_NAME);
    this.rowMapper = (rs, rowNum) -> this.eventMapper.read(rs.getString("event_data"));
    this.insertSql = ("INSERT INTO %s "
        + "(event_time, principal, event_type, application_name, correlation_id, event_data) "
        + "VALUES (?, ?, ?, ?, ?, ?)").formatted(table);
    this.selectSql = "SELECT event_data FROM %s".formatted(table);
  }

  /** {@inheritDoc} */
  @Override
  public void save(final @NonNull AuditEvent event) {
    final se.swedenconnect.spring.audit.AuditEvent structured =
        event instanceof final se.swedenconnect.spring.audit.AuditEvent e ? e : null;
    final String applicationName = structured != null && structured.getApplicationName() != null
        ? structured.getApplicationName().getName() : null;
    final String correlationId = structured != null && structured.getCorrelationId() != null
        ? structured.getCorrelationId().getValue() : null;

    this.jdbcTemplate.update(this.insertSql,
        LocalDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC),
        event.getPrincipal(),
        event.getType(),
        applicationName,
        correlationId,
        this.eventMapper.write(event));
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull List<AuditEvent> find(
      final @Nullable String principal, final @Nullable Instant after, final @Nullable String type) {

    final List<String> conditions = new ArrayList<>();
    final List<Object> arguments = new ArrayList<>();
    if (principal != null) {
      conditions.add("principal = ?");
      arguments.add(principal);
    }
    if (after != null) {
      conditions.add("event_time > ?");
      arguments.add(LocalDateTime.ofInstant(after, ZoneOffset.UTC));
    }
    if (type != null) {
      conditions.add("event_type = ?");
      arguments.add(type);
    }
    final String sql = this.selectSql
        + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
        + " ORDER BY event_time DESC, id DESC";
    return this.jdbcTemplate.query(sql, this.rowMapper, arguments.toArray());
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull List<AuditEvent> findRecent(final int limit) {
    return this.jdbcTemplate.query(
        connection -> {
          final var ps = connection.prepareStatement(this.selectSql + " ORDER BY event_time DESC, id DESC");
          if (limit > 0) {
            ps.setMaxRows(limit);
          }
          return ps;
        },
        this.rowMapper);
  }

}
