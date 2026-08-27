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
package se.swedenconnect.spring.audit.value;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * A specialization of {@link AuditValue} that holds an {@link Instant} as its value.
 *
 * @author Martin Lindström
 */
public class InstantAuditValue extends AuditValue<Instant> {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Constructor setting up an {@code InstantAuditValue} where the value is the current time.
   *
   * @param name the name of the audit value
   */
  public InstantAuditValue(final @NonNull String name) {
    super(name, Instant.now());
  }

  /**
   * Constructor accepting an {@link Instant} as the value.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public InstantAuditValue(final @NonNull String name, final @Nullable Instant value) {
    super(name, value);
  }

  /**
   * Constructor accepting a {@link Date} as the value. The date is converted to an {@link Instant}.
   *
   * @param name the name of the audit value
   * @param value the value of the audit value (may be {@code null})
   */
  public InstantAuditValue(final @NonNull String name, final @Nullable Date value) {
    super(name, value != null ? value.toInstant() : null);
  }

  /**
   * Constructor that assigns the current {@link Instant} obtained from the supplied {@link Clock} as the value.
   *
   * @param name the name of the audit value
   * @param clock the clock to obtain the current instant from (may be {@code null})
   */
  public InstantAuditValue(final @NonNull String name, final @Nullable Clock clock) {
    super(name, clock != null ? clock.instant() : null);
  }

}
