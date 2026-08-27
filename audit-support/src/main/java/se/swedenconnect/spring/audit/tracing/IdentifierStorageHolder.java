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
package se.swedenconnect.spring.audit.tracing;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Holds the {@link IdentifierStorage} that {@link CorrelationIDHolder} and {@link TraceIDHolder} read and write
 * through.
 * <p>
 * Until another implementation is installed, a {@link MdcIdentifierStorage} is used, so that the library works with no
 * configuration at all. An application that needs another storage - typically one that hops threads and therefore can
 * not use the thread bound MDC - installs its own implementation using {@link #setStorage(IdentifierStorage)} during
 * startup, before any audit event is created.
 * </p>
 * <p>
 * Installing a storage replaces the one currently installed. Re-installation is deliberately allowed rather than
 * refused: this is process wide state, and a test that could not restore it would leak into the tests that follow.
 * {@link #resetStorage()} restores the default implementation, and is what a test should call when it is done.
 * </p>
 *
 * @author Martin Lindström
 */
public class IdentifierStorageHolder {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(IdentifierStorageHolder.class);

  /** The storage used unless another implementation has been installed. */
  private static final IdentifierStorage DEFAULT_STORAGE = new MdcIdentifierStorage();

  /** The currently installed storage. */
  private static volatile IdentifierStorage storage = DEFAULT_STORAGE;

  /**
   * Gets the currently installed {@link IdentifierStorage}.
   *
   * @return the currently installed {@link IdentifierStorage}, never {@code null}
   */
  public static @NonNull IdentifierStorage getStorage() {
    return storage;
  }

  /**
   * Installs the {@link IdentifierStorage} to use, replacing the one currently installed.
   *
   * @param storage the storage to install
   * @throws NullPointerException if {@code storage} is {@code null} - use {@link #resetStorage()} to restore the
   *     default implementation
   */
  public static void setStorage(final @NonNull IdentifierStorage storage) {
    IdentifierStorageHolder.storage = Objects.requireNonNull(storage, "storage must not be null");
    log.info("Installed audit identifier storage: {}", storage.getClass().getName());
  }

  /**
   * Restores the default {@link MdcIdentifierStorage}. Mainly intended for tests, which should call this once they are
   * done with an installed storage, so that it does not leak into subsequent tests.
   */
  public static void resetStorage() {
    storage = DEFAULT_STORAGE;
  }

  // Hidden constructor
  private IdentifierStorageHolder() {
  }

}
