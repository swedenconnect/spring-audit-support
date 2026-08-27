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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link CorrelationIDHolder}, {@link TraceIDHolder} and {@link TraceIDWriter}, together with the
 * {@link IdentifierStorage} they read and write through.
 *
 * @author Martin Lindström
 */
class IdentifierHolderTest {

  @AfterEach
  void clear() {
    IdentifierStorageHolder.resetStorage();
    MDC.clear();
  }

  @Test
  void testDefaultStorageIsMdc() {
    assertThat(IdentifierStorageHolder.getStorage()).isInstanceOf(MdcIdentifierStorage.class);
  }

  @Test
  void testCorrelationIdIsStoredInMdcByDefault() {
    CorrelationIDHolder.set(CorrelationID.of("abc-123"));

    assertThat(MDC.get(CorrelationIDHolder.CORRELATION_ID_KEY)).isEqualTo("abc-123");
    assertThat(CorrelationIDHolder.get()).isEqualTo(CorrelationID.of("abc-123"));
  }

  @Test
  void testCorrelationIdRoundTrip() {
    assertThat(CorrelationIDHolder.get()).isNull();

    CorrelationIDHolder.set(CorrelationID.of("abc-123"));
    assertThat(CorrelationIDHolder.get()).isEqualTo(CorrelationID.of("abc-123"));

    CorrelationIDHolder.set(CorrelationID.of("other"));
    assertThat(CorrelationIDHolder.get()).isEqualTo(CorrelationID.of("other"));

    CorrelationIDHolder.clear();
    assertThat(CorrelationIDHolder.get()).isNull();
  }

  @Test
  void testClearCorrelationIdWhenNoneIsAssigned() {
    // Cleanup must never fail - it is called from finally blocks.
    CorrelationIDHolder.clear();

    assertThat(CorrelationIDHolder.get()).isNull();
  }

  @Test
  void testSetNullCorrelationIdThrows() {
    assertThatNullPointerException().isThrownBy(() -> CorrelationIDHolder.set(null));
  }

  @Test
  void testBlankStoredValueIsTreatedAsNoValue() {
    MDC.put(CorrelationIDHolder.CORRELATION_ID_KEY, "");
    MDC.put(TraceIDHolder.TRACE_ID_KEY, " ");

    assertThat(CorrelationIDHolder.get()).isNull();
    assertThat(TraceIDHolder.get()).isNull();
  }

  @Test
  void testTraceIdRoundTrip() {
    assertThat(TraceIDHolder.get()).isNull();

    TraceIDWriter.set(TraceID.of("4bf92f3577b34da6a3ce929d0e0e4736"));
    assertThat(TraceIDHolder.get()).isEqualTo(TraceID.of("4bf92f3577b34da6a3ce929d0e0e4736"));

    TraceIDWriter.clear();
    assertThat(TraceIDHolder.get()).isNull();
  }

  @Test
  void testSetNullTraceIdThrows() {
    assertThatNullPointerException().isThrownBy(() -> TraceIDWriter.set(null));
  }

  @Test
  void testTraceIdHolderIsReadOnly() {
    // The application facing surface of the trace ID holder offers no way of assigning a value - a trace ID is
    // assigned by the infrastructure, using TraceIDWriter.
    assertThat(TraceIDHolder.class.getMethods())
        .filteredOn(m -> m.getDeclaringClass().equals(TraceIDHolder.class))
        .extracting(Method::getName)
        .containsExactly("get");
  }

  @Test
  void testTraceIdUsesItsOwnKey() {
    // The key must not collide with the traceId key used by Micrometer Tracing.
    assertThat(TraceIDHolder.TRACE_ID_KEY).isNotEqualTo("traceId").isNotEqualToIgnoringCase("traceId");
  }

  @Test
  void testInstalledStorageIsUsed() {
    final RecordingStorage storage = new RecordingStorage();
    IdentifierStorageHolder.setStorage(storage);

    CorrelationIDHolder.set(CorrelationID.of("abc-123"));
    TraceIDWriter.set(TraceID.of("trace-1"));

    assertThat(storage.values)
        .containsEntry(CorrelationIDHolder.CORRELATION_ID_KEY, "abc-123")
        .containsEntry(TraceIDHolder.TRACE_ID_KEY, "trace-1");
    assertThat(CorrelationIDHolder.get()).isEqualTo(CorrelationID.of("abc-123"));
    assertThat(TraceIDHolder.get()).isEqualTo(TraceID.of("trace-1"));

    // ... and nothing ended up in the MDC.
    assertThat(MDC.get(CorrelationIDHolder.CORRELATION_ID_KEY)).isNull();
    assertThat(MDC.get(TraceIDHolder.TRACE_ID_KEY)).isNull();
  }

  @Test
  void testInstallingAStorageReplacesThePreviousOne() {
    final RecordingStorage first = new RecordingStorage();
    final RecordingStorage second = new RecordingStorage();

    IdentifierStorageHolder.setStorage(first);
    IdentifierStorageHolder.setStorage(second);
    CorrelationIDHolder.set(CorrelationID.of("abc-123"));

    assertThat(IdentifierStorageHolder.getStorage()).isSameAs(second);
    assertThat(first.values).isEmpty();
    assertThat(second.values).containsEntry(CorrelationIDHolder.CORRELATION_ID_KEY, "abc-123");
  }

  @Test
  void testSetNullStorageThrows() {
    assertThatNullPointerException().isThrownBy(() -> IdentifierStorageHolder.setStorage(null));
  }

  @Test
  void testResetStorageRestoresTheDefault() {
    IdentifierStorageHolder.setStorage(new RecordingStorage());
    IdentifierStorageHolder.resetStorage();

    assertThat(IdentifierStorageHolder.getStorage()).isInstanceOf(MdcIdentifierStorage.class);

    CorrelationIDHolder.set(CorrelationID.of("abc-123"));
    assertThat(MDC.get(CorrelationIDHolder.CORRELATION_ID_KEY)).isEqualTo("abc-123");
  }

  /**
   * An {@link IdentifierStorage} keeping its values in a map, i.e., without any notion of a current thread or request.
   */
  private static class RecordingStorage implements IdentifierStorage {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public @Nullable String get(final @NonNull String key) {
      return this.values.get(key);
    }

    @Override
    public void put(final @NonNull String key, final @NonNull String value) {
      this.values.put(key, value);
    }

    @Override
    public void clear(final @NonNull String key) {
      this.values.remove(key);
    }
  }

}
