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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * An {@link IdentifierStorage} that keeps the identifiers in the SLF4J MDC (Mapped Diagnostic Context).
 * <p>
 * This is the implementation used unless another one has been installed, and it is what makes the library work with no
 * configuration at all. Since the MDC is thread bound in every SLF4J backend, it fits an application where one thread
 * handles a request from start to end. An application that hops threads between the start and the end of a request
 * must install an implementation that follows its own context - a value read from the MDC on another thread is either
 * missing, or, worse, left over from an unrelated earlier request.
 * </p>
 * <p>
 * A side effect of keeping the identifiers in the MDC is that they can be included in the application's ordinary log
 * records by referring to the keys in the logging configuration.
 * </p>
 *
 * @author Martin Lindström
 */
public class MdcIdentifierStorage implements IdentifierStorage {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(MdcIdentifierStorage.class);

  /** {@inheritDoc} */
  @Override
  public @Nullable String get(final @NonNull String key) {
    try {
      return MDC.get(Objects.requireNonNull(key, "key must not be null"));
    }
    catch (final Exception e) {
      log.warn("Failed to read '{}' from MDC", key, e);
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void put(final @NonNull String key, final @NonNull String value) {
    try {
      MDC.put(Objects.requireNonNull(key, "key must not be null"),
          Objects.requireNonNull(value, "value must not be null"));
    }
    catch (final Exception e) {
      log.warn("Failed to store '{}' in MDC", key, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clear(final @NonNull String key) {
    try {
      MDC.remove(Objects.requireNonNull(key, "key must not be null"));
    }
    catch (final Exception e) {
      log.warn("Failed to remove '{}' from MDC", key, e);
    }
  }

}
