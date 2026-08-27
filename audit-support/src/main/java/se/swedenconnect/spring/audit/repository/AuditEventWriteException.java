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
import se.swedenconnect.spring.audit.LibraryVersion;

import java.io.Serial;

/**
 * Exception thrown when an audit event cannot be written to its repository.
 * <p>
 * A write failure is always logged at {@code ERROR}. Whether it additionally results in this exception being thrown is
 * controlled by {@link AbstractAuditEventRepository#setThrowOnWriteFail(boolean)}.
 * </p>
 *
 * @author Martin Lindström
 */
public class AuditEventWriteException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = LibraryVersion.SERIAL_VERSION_UID;

  /**
   * Constructor.
   *
   * @param message the detail message
   */
  public AuditEventWriteException(final @NonNull String message) {
    super(message);
  }

  /**
   * Constructor.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public AuditEventWriteException(final @NonNull String message, final @Nullable Throwable cause) {
    super(message, cause);
  }

}
