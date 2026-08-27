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
package se.swedenconnect.spring.audit.autoconfigure;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import se.swedenconnect.spring.audit.AuditApplicationListener;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventContextResolver;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.transform.EventTransformer;
import se.swedenconnect.spring.audit.support.ApplicationName;

/**
 * Auto-configuration for the Sweden Connect Spring Audit support.
 * <p>
 * Registers an {@link AuditApplicationListener} wired with all {@link EventTransformer} beans found in the application
 * context, an {@link AuditEventContextResolver} and an {@link ApplicationName} bean. All of them are only created if
 * the application has not already declared beans of these types.
 * </p>
 *
 * @author Martin Lindström
 */
@AutoConfiguration(after = ProjectInfoAutoConfiguration.class)
@EnableConfigurationProperties(AuditSupportProperties.class)
public class AuditSupportAutoConfiguration {

  /** The Spring environment, used to resolve the application name. */
  private final Environment environment;

  /**
   * Constructor.
   *
   * @param environment the Spring environment
   */
  public AuditSupportAutoConfiguration(final @NonNull Environment environment) {
    this.environment = environment;
  }

  /**
   * Creates a {@link DefaultAuditEventContextResolver} bean, unless the application has already declared an
   * {@link AuditEventContextResolver} bean.
   * <p>
   * The resolver is set up with the {@link ApplicationName} bean, and with the default principal configured via
   * {@code audit.default-principal}, i.e., the principal that the resolved context reports when no user is
   * authenticated. If this property is not assigned, the context reports no principal at all. A suggested value is
   * {@link AuditEvent#SYSTEM_PRINCIPAL}.
   * </p>
   *
   * @param applicationName the application name
   * @param properties the audit support properties
   * @return an {@link AuditEventContextResolver}
   */
  @Bean
  @ConditionalOnMissingBean
  @NonNull AuditEventContextResolver auditEventContextResolver(final @NonNull ApplicationName applicationName,
      final @NonNull AuditSupportProperties properties) {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(applicationName);
    resolver.setDefaultPrincipal(properties.getDefaultPrincipal());
    return resolver;
  }

  /**
   * Creates the {@link AuditApplicationListener} bean.
   *
   * @param publisher the application event publisher
   * @param auditEventContextResolver the resolver giving the transformers their {@code AuditEventContext}
   * @param eventTransformers the registered {@link EventTransformer} beans
   * @return an {@link AuditApplicationListener}
   */
  @Bean
  @ConditionalOnMissingBean
  @NonNull AuditApplicationListener auditApplicationListener(final @NonNull ApplicationEventPublisher publisher,
      final @NonNull AuditEventContextResolver auditEventContextResolver,
      final @NonNull ObjectProvider<EventTransformer> eventTransformers) {
    return new AuditApplicationListener(
        publisher, auditEventContextResolver, eventTransformers.orderedStream().toList());
  }

  /**
   * Creates the {@link ApplicationName} bean.
   * <p>
   * The name is resolved from the first available of: the {@code spring.application.name} property, or the artifact
   * from the {@link BuildProperties} (if available).
   * </p>
   *
   * @param buildProperties the build properties, if available
   * @return an {@link ApplicationName}
   * @throws BeanCreationException if no application name can be resolved
   */
  @Bean
  @ConditionalOnMissingBean
  @NonNull ApplicationName applicationName(final @Nullable BuildProperties buildProperties)
      throws BeanCreationException {

    final String applicationName = Optional.ofNullable(this.environment.getProperty("spring.application.name"))
        .orElseGet(() -> Optional.ofNullable(buildProperties)
            .map(BuildProperties::getArtifact)
            .orElseThrow(() -> new BeanCreationException(
                "No application name available - Failed to create ApplicationName bean")));

    return new ApplicationName(applicationName);
  }

}
