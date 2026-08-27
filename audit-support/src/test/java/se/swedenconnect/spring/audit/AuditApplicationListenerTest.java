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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.transform.EventTransformer;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link AuditApplicationListener}.
 *
 * @author Martin Lindström
 */
class AuditApplicationListenerTest {

  private static AuditEvent auditEvent() {
    return new AuditEvent("alice", "test-type", Map.of());
  }

  private static AuditEventContextResolver contextResolver() {
    return new DefaultAuditEventContextResolver(new ApplicationName("test-app"));
  }

  @AfterEach
  void clear() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void testNullPublisherThrows() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AuditApplicationListener(null, contextResolver(), List.of()));
  }

  @Test
  void testNullContextResolverThrows() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    assertThatNullPointerException()
        .isThrownBy(() -> new AuditApplicationListener(publisher, null, List.of()));
  }

  @Test
  void testNullTransformersIsAllowed() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), null);

    listener.onApplicationEvent(new TestEvent("no-match"));

    verifyNoInteractions(publisher);
  }

  @Test
  void testAuditApplicationEventIsIgnored() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());

    listener.onApplicationEvent(new AuditApplicationEvent(auditEvent()));

    verifyNoInteractions(publisher);
  }

  @Test
  void testPayloadApplicationEventWithAuditEventIsPublished() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());
    final AuditEvent event = auditEvent();

    listener.onApplicationEvent(new PayloadApplicationEvent<>("src", event));

    assertThat(capturePublished(publisher).getAuditEvent()).isSameAs(event);
  }

  @Test
  void testPayloadApplicationEventWithNonAuditEventIsIgnored() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());

    listener.onApplicationEvent(new PayloadApplicationEvent<>("src", "just-a-string"));

    verifyNoInteractions(publisher);
  }

  @Test
  void testEventWithAuditEventAsSourceIsPublished() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());
    final AuditEvent event = auditEvent();

    listener.onApplicationEvent(new TestEvent(event));

    assertThat(capturePublished(publisher).getAuditEvent()).isSameAs(event);
  }

  @Test
  void testSelfTransformingEventIsPublished() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());
    final AuditEvent event = auditEvent();

    listener.onApplicationEvent(new TransformingEvent(true, event));

    assertThat(capturePublished(publisher).getAuditEvent()).isSameAs(event);
  }

  @Test
  void testSelfTransformingEventThatDoesNotSupportItselfIsIgnored() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditApplicationListener listener = new AuditApplicationListener(publisher, contextResolver(), List.of());

    listener.onApplicationEvent(new TransformingEvent(false, auditEvent()));

    verifyNoInteractions(publisher);
  }

  @Test
  void testRegisteredTransformerIsUsed() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditEvent event = auditEvent();
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(event);

    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(transformer));
    final TestEvent applicationEvent = new TestEvent("src");
    listener.onApplicationEvent(applicationEvent);

    verify(transformer).transform(eq(applicationEvent), any());
    assertThat(capturePublished(publisher).getAuditEvent()).isSameAs(event);
  }

  @Test
  void testMetadataContainsPrincipalFromSecurityContext() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(auditEvent());

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("bob", null, List.of()));
    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(transformer));
    listener.onApplicationEvent(new TestEvent("src"));

    final ArgumentCaptor<AuditEventContext> captor =
        ArgumentCaptor.forClass(AuditEventContext.class);
    verify(transformer).transform(any(), captor.capture());
    assertThat(captor.getValue().getPrincipal()).isEqualTo("bob");
  }

  @Test
  void testContextIsSuppliedByTheContextResolver() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(auditEvent());

    final AuditEventContext context = mock(AuditEventContext.class);
    final AuditEventContextResolver resolver = mock(AuditEventContextResolver.class);
    when(resolver.getContext(any())).thenReturn(context);

    final AuditApplicationListener listener = new AuditApplicationListener(publisher, resolver, List.of(transformer));
    listener.onApplicationEvent(new TestEvent("src"));

    verify(transformer).transform(any(), eq(context));
  }

  @Test
  void testEventIsPassedAsInputToTheContextResolver() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(auditEvent());

    final AuditEventContextResolver resolver = mock(AuditEventContextResolver.class);
    when(resolver.getContext(any())).thenReturn(mock(AuditEventContext.class));

    final AuditApplicationListener listener = new AuditApplicationListener(publisher, resolver, List.of(transformer));
    final TestEvent applicationEvent = new TestEvent("src");
    listener.onApplicationEvent(applicationEvent);

    verify(resolver).getContext(eq(applicationEvent));
  }

  @Test
  void testContextResolverIsNotInvokedForEventsThatAreNotTransformed() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditEventContextResolver resolver = mock(AuditEventContextResolver.class);

    final AuditApplicationListener listener = new AuditApplicationListener(publisher, resolver, List.of());
    listener.onApplicationEvent(new TestEvent("no-match"));

    verifyNoInteractions(resolver);
  }

  @Test
  void testApplicationNameFromContextResolverIsHandedToTransformer() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(auditEvent());

    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(transformer));
    listener.onApplicationEvent(new TestEvent("src"));

    final ArgumentCaptor<AuditEventContext> captor = ArgumentCaptor.forClass(AuditEventContext.class);
    verify(transformer).transform(any(), captor.capture());
    assertThat(captor.getValue().getApplicationName()).isEqualTo(new ApplicationName("test-app"));
  }

  @Test
  void testNonMatchingTransformerIsIgnored() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(false);

    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(transformer));
    listener.onApplicationEvent(new TestEvent("src"));

    verify(transformer, never()).transform(any(), any());
    verifyNoInteractions(publisher);
  }

  @Test
  void testFirstMatchingTransformerIsUsed() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final AuditEvent event = auditEvent();
    final EventTransformer first = mock(EventTransformer.class);
    final EventTransformer second = mock(EventTransformer.class);
    when(first.supports(any())).thenReturn(true);
    when(first.transform(any(), any())).thenReturn(event);

    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(first, second));
    listener.onApplicationEvent(new TestEvent("src"));

    verify(first).transform(any(), any());
    verifyNoInteractions(second);
    assertThat(capturePublished(publisher).getAuditEvent()).isSameAs(event);
  }

  @Test
  void testTransformerReturningNullPublishesNothing() {
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final EventTransformer transformer = mock(EventTransformer.class);
    when(transformer.supports(any())).thenReturn(true);
    when(transformer.transform(any(), any())).thenReturn(null);

    final AuditApplicationListener listener =
        new AuditApplicationListener(publisher, contextResolver(), List.of(transformer));
    listener.onApplicationEvent(new TestEvent("src"));

    verifyNoInteractions(publisher);
  }

  private static AuditApplicationEvent capturePublished(final ApplicationEventPublisher publisher) {
    final ArgumentCaptor<AuditApplicationEvent> captor = ArgumentCaptor.forClass(AuditApplicationEvent.class);
    verify(publisher).publishEvent(captor.capture());
    return captor.getValue();
  }

  /**
   * A plain {@link ApplicationEvent} whose source is whatever is passed to the constructor.
   */
  private static class TestEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    TestEvent(final Object source) {
      super(source);
    }
  }

  /**
   * An {@link ApplicationEvent} that is itself an {@link EventTransformer}.
   */
  private static class TransformingEvent extends ApplicationEvent implements EventTransformer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean supports;

    private final AuditEvent result;

    TransformingEvent(final boolean supports, final AuditEvent result) {
      super("src");
      this.supports = supports;
      this.result = result;
    }

    @Override
    public @NonNull AuditEvent transform(final @NonNull ApplicationEvent event,
        final @NonNull AuditEventContext context) {
      return this.result;
    }

    @Override
    public boolean supports(final @NonNull ApplicationEvent event) {
      return this.supports;
    }
  }
}
