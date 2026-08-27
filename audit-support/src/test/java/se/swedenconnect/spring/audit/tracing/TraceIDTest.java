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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Test cases for {@link TraceID}.
 *
 * @author Martin Lindström
 */
class TraceIDTest {

  private static final String W3C_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Test
  void testCreate() {
    assertThat(TraceID.of(W3C_TRACE_ID).getValue()).isEqualTo(W3C_TRACE_ID);
    assertThat(TraceID.of(W3C_TRACE_ID)).hasToString(W3C_TRACE_ID);
  }

  @Test
  void testValueIsNotRequiredToBeAW3CTraceId() {
    // Another tracing framework may produce a value that looks different - it is still kept as-is.
    assertThat(TraceID.of("trace-42").getValue()).isEqualTo("trace-42");
  }

  @Test
  void testNullValueThrows() {
    assertThatNullPointerException().isThrownBy(() -> new TraceID(null));
  }

  @Test
  void testEmptyValueThrows() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new TraceID(" "));
  }

  @Test
  void testEqualsAndHashCode() {
    final TraceID id = TraceID.of(W3C_TRACE_ID);

    assertThat(id).isEqualTo(id);
    assertThat(id).isEqualTo(TraceID.of(W3C_TRACE_ID));
    assertThat(id).hasSameHashCodeAs(TraceID.of(W3C_TRACE_ID));
    assertThat(id).isNotEqualTo(TraceID.of("other"));
    assertThat(id).isNotEqualTo(null);
    assertThat(id.equals(W3C_TRACE_ID)).isFalse();
  }

}
