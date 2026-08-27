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

import java.util.Optional;

/**
 * Utility constants for creating common audit values.
 *
 * @author Martin Lindström
 */
public class AuditValueConstants {

  /**
   * Returns a {@link StringAuditValue} representing a user identity.
   *
   * @param userId the user identity (or {@code null}).
   * @return a {@link StringAuditValue} representing a user identity
   */
  public static StringAuditValue userId(final @Nullable String userId) {
    return new StringAuditValue("user_id", userId);
  }

  /**
   * Returns a {@link StringAuditValue} representing a display name.
   *
   * @param displayName the display name (or {@code null}).
   * @return a {@link StringAuditValue} representing a display name
   */
  public static StringAuditValue displayName(final @Nullable String displayName) {
    return new StringAuditValue("display_name", displayName);
  }

  /**
   * Returns a {@link StringAuditValue} representing a given name.
   *
   * @param givenName the given name (or {@code null}).
   * @return a {@link StringAuditValue} representing a given name
   */
  public static StringAuditValue givenName(final @Nullable String givenName) {
    return new StringAuditValue("given_name", givenName);
  }

  /**
   * Returns a {@link StringAuditValue} representing a surname.
   *
   * @param surname the surname (or {@code null}).
   * @return a {@link StringAuditValue} representing a surname
   */
  public static StringAuditValue surname(final @Nullable String surname) {
    return new StringAuditValue("sn", surname);
  }

  /**
   * Returns a {@link StringAuditValue} representing an email address.
   *
   * @param email the email address (or {@code null}).
   * @return a {@link StringAuditValue} representing an email address
   */
  public static StringAuditValue email(final @Nullable String email) {
    return new StringAuditValue("email", email);
  }

  /**
   * Returns a {@link StringAuditValue} representing a personal identity number.
   *
   * @param personalIdentityNumber the personal identity number (or {@code null}).
   * @return a {@link StringAuditValue} representing a personal identity number
   */
  public static StringAuditValue personalIdentityNumber(final @Nullable String personalIdentityNumber) {
    return new StringAuditValue("personal_identity_number", personalIdentityNumber);
  }

  /**
   * Returns a {@link MapAuditValue} representing an error. This value contains the following members:
   * <ul>
   *   <li>{@code code} - The error code.</li>
   *   <li>{@code message} - An optional error message.</li>
   *   <li>{@code exception} - Optional exception class.</li>
   *   <li>{@code details} - Optional error details.</li>
   * </ul>
   *
   * @param code the error code
   * @param message optional error message
   * @param exception optional exception that led to this error
   * @param details optional error details
   * @return a {@link MapAuditValue} representing an error
   */
  public static MapAuditValue error(final @NonNull String code, final @Nullable String message,
      final @Nullable Class<?> exception, final @Nullable String details) {
    return MapAuditValue.builder()
        .name("error")
        .value("code", code)
        .value("message", message)
        .value("exception_class",
            Optional.ofNullable(exception).map(Class::getName).orElse(null))
        .value("details", details)
        .build();
  }

  // Hidden constructor
  private AuditValueConstants() {
  }
}
