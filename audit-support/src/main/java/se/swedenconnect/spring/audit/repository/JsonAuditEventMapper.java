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
import org.springframework.boot.actuate.audit.AuditEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * A JSON {@link AuditEventMapper}.
 * <p>
 * Events are read back as {@link se.swedenconnect.spring.audit.AuditEvent structured audit events}, so that the
 * {@code application_name}, {@code correlation_id} and any additional root-level fields survive a
 * {@link #write(AuditEvent) write}/{@link #read(String) read} round trip.
 * </p>
 *
 * @author Martin Lindström
 */
public class JsonAuditEventMapper implements AuditEventMapper {

  /** The underlying {@link ObjectMapper}. */
  private final ObjectMapper mapper;

  /**
   * Constructor.
   *
   * @param mapper the {@link ObjectMapper}
   */
  public JsonAuditEventMapper(final @NonNull ObjectMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  /**
   * {@inheritDoc}
   * <p>
   * The event is serialized using its runtime type, so that the fields added by
   * {@link se.swedenconnect.spring.audit.AuditEvent} (if present) are included.
   * </p>
   */
  @Override
  public @NonNull String write(final @NonNull AuditEvent event) throws AuditEventMappingException {
    try {
      return this.mapper.writeValueAsString(event);
    }
    catch (final JacksonException e) {
      throw new AuditEventMappingException("Failed to serialize audit event", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public @NonNull AuditEvent read(final @NonNull String event) throws AuditEventMappingException {
    try {
      return this.mapper.readValue(event, se.swedenconnect.spring.audit.AuditEvent.class);
    }
    catch (final JacksonException e) {
      throw new AuditEventMappingException("Failed to deserialize audit event", e);
    }
  }

}
