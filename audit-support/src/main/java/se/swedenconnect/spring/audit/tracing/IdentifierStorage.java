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

/**
 * The strategy deciding where the identifiers of the current flow are kept.
 * <p>
 * This is the only contract between the audit library and the environment it runs in. It deliberately says nothing
 * about threads, requests or HTTP: it stores a string under a key, and what "current" means is entirely up to the
 * implementation. A servlet application is served by {@link MdcIdentifierStorage}, which keeps the values in the SLF4J
 * MDC, whereas an application that hops threads supplies an implementation that follows its own context.
 * </p>
 * <p>
 * Implementations must be safe for concurrent use, and none of the methods may throw - an identifier is diagnostic
 * information, and a failure to store or read it must never break the operation being audited. An implementation that
 * cannot carry out an operation logs it and returns.
 * </p>
 *
 * @author Martin Lindström
 * @see IdentifierStorageHolder
 */
public interface IdentifierStorage {

  /**
   * Gets the value stored under the given key for the current flow.
   *
   * @param key the key
   * @return the value, or {@code null} if no value is available
   */
  @Nullable String get(final @NonNull String key);

  /**
   * Stores the given value under the given key for the current flow, replacing any value previously stored under that
   * key.
   *
   * @param key the key
   * @param value the value to store
   */
  void put(final @NonNull String key, final @NonNull String value);

  /**
   * Removes the value stored under the given key for the current flow. Removing a key that holds no value is not an
   * error.
   *
   * @param key the key
   */
  void clear(final @NonNull String key);

}
