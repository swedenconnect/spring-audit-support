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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import se.swedenconnect.spring.audit.transform.EventTransformer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An {@link ApplicationListener} that turns Spring {@link ApplicationEvent}s into audit events and (re-)publishes them
 * wrapped in an {@link AuditApplicationEvent} so that Spring Boot's auditing infrastructure picks them up.
 * <p>
 * For each received event the listener applies the following resolution, in order:
 * </p>
 * <ul>
 *   <li>{@link AuditApplicationEvent}s are ignored, since they have already been processed.</li>
 *   <li>A {@link PayloadApplicationEvent} carrying an {@link AuditEvent} payload is published as an
 *       {@link AuditApplicationEvent}.</li>
 *   <li>An event whose {@link ApplicationEvent#getSource() source} is an {@link AuditEvent} is published as an
 *       {@link AuditApplicationEvent}.</li>
 *   <li>An event that itself implements {@link EventTransformer} and {@link EventTransformer#supports(ApplicationEvent)
 *       supports} itself is transformed and published.</li>
 *   <li>Otherwise the first registered {@link EventTransformer} that supports the event is used to transform and
 *       publish it. If no transformer matches, the event is ignored.</li>
 * </ul>
 * <p>
 * Before a transformer is invoked, the listener asks its {@link AuditEventContextResolver} for an
 * {@link AuditEventContext}. The {@link ApplicationEvent} being audited is passed along as input to the resolver, which
 * means that a custom resolver may base its result on the event.
 * </p>
 * <p>
 * The {@code AuditApplicationListener} must be registered as a Spring component (bean) for it to receive application
 * events.
 * </p>
 *
 * @author Martin Lindström
 */
public class AuditApplicationListener implements ApplicationListener<ApplicationEvent> {

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(AuditApplicationListener.class);

  /** The system event publisher. */
  private final @NonNull ApplicationEventPublisher publisher;

  /** The registered event transformers used to turn application events into audit events. */
  private final @NonNull List<EventTransformer> eventTransformers;

  /** The context resolver for passing contexts to the transformers. */
  private final @NonNull AuditEventContextResolver auditEventContextResolver;

  /**
   * Constructor.
   *
   * @param publisher the event publisher used to publish the resulting {@link AuditApplicationEvent}s
   * @param auditEventContextResolver the resolver used to obtain the {@link AuditEventContext} that is handed to the
   *     transformers
   * @param eventTransformers the event transformers to apply, or {@code null} for none
   */
  public AuditApplicationListener(final @NonNull ApplicationEventPublisher publisher,
      final @NonNull AuditEventContextResolver auditEventContextResolver,
      final @Nullable List<EventTransformer> eventTransformers) {
    this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    this.auditEventContextResolver =
        Objects.requireNonNull(auditEventContextResolver, "auditEventContextResolver must not be null");
    this.eventTransformers = eventTransformers != null ? List.copyOf(eventTransformers) : Collections.emptyList();
  }

  /** {@inheritDoc} */
  @Override
  public void onApplicationEvent(final @NonNull ApplicationEvent event) {

    // We don't handle audit events - they have already been processed ...
    //
    if (event instanceof AuditApplicationEvent) {
      return;
    }

    // If this is a PayloadApplicationEvent holding an AuditEvent, we publish it.
    // This only happens if someone publishes an AuditEvent without wrapping it in an AuditApplicationEvent.
    //
    if (event instanceof final PayloadApplicationEvent<?> payloadEvent) {
      if (payloadEvent.getPayload() instanceof final AuditEvent auditEvent) {
        log.debug("Received AuditEvent in PayloadApplicationEvent - publishing '{}'", auditEvent.getType());
        this.publisher.publishEvent(new AuditApplicationEvent(auditEvent));
        return;
      }
    }

    // Another odd case to handle is if someone has created an ApplicationEvent and uses an AuditEvent as its source.
    //
    if (event.getSource() instanceof final AuditEvent auditEvent) {
      log.debug("Received AuditEvent in source of ApplicationEvent - publishing '{}'", auditEvent.getType());
      this.publisher.publishEvent(new AuditApplicationEvent(auditEvent));
      return;
    }

    // If the event itself implements EventTransformer, we use this to create the AuditEvent ...
    //
    if (event instanceof final EventTransformer transformer) {
      if (transformer.supports(event)) {
        this.transformAndPublish(event, transformer);
        return;
      }
    }

    // Find a suitable EventTransformer for the event. If no transformer is found, we ignore the event ...
    //
    this.eventTransformers.stream()
        .filter(e -> e.supports(event))
        .findFirst()
        .ifPresent(transformer -> this.transformAndPublish(event, transformer));
  }

  /**
   * Transforms the supplied event using the given transformer and publishes the resulting {@link AuditEvent} wrapped in
   * an {@link AuditApplicationEvent}. If the transformer returns {@code null}, a warning is logged and nothing is
   * published.
   * <p>
   * The {@link AuditEventContext} handed to the transformer is obtained from the listener's
   * {@link AuditEventContextResolver}, and the event being audited is passed along as input to the resolver, so that a
   * custom resolver may base its result on the event.
   * </p>
   *
   * @param event the event to transform
   * @param transformer the transformer to apply
   */
  private void transformAndPublish(final @NonNull ApplicationEvent event, final @NonNull EventTransformer transformer) {
    final AuditEvent auditEvent = transformer.transform(event, this.auditEventContextResolver.getContext(event));
    if (auditEvent == null) {
      log.warn("Transformer '{}' returned null for event '{}' - check implementation!",
          transformer.getClass().getName(), event.getClass().getName());
      return;
    }
    this.publisher.publishEvent(new AuditApplicationEvent(auditEvent));
    log.trace("Transformed '{}' to AuditEvent - publishing '{}'", event.getClass().getName(), auditEvent.getType());
  }

}
