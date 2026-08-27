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
package se.swedenconnect.spring.audit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an Audit type.
 *
 * @param type the audit type
 */
public record AuditType(@NonNull String type) implements Serializable {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  public AuditType {
    if (!StringUtils.hasText(type)) {
      throw new IllegalArgumentException("type must be set");
    }
  }

  /**
   * Creates a {@link AuditType} object
   *
   * @param auditType the type name
   * @return a {@link AuditType} object
   */
  public static AuditType of(final @NonNull String auditType) {
    return new AuditType(auditType);
  }

  @Override
  public @NonNull String toString() {
    return this.type;
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }
    final AuditType that = (AuditType) o;
    return Objects.equals(this.type, that.type);
  }

}
