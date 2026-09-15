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

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import se.swedenconnect.spring.audit.AuditApplicationListener;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventContextResolver;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.transform.ApplicationReadyEventTransformer;
import se.swedenconnect.spring.audit.transform.ContextClosedEventTransformer;
import se.swedenconnect.spring.audit.transform.EventTransformer;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.ApplicationVersion;

/**
 * Auto-configuration for the Sweden Connect Spring Audit support.
 * <p>
 * Registers an {@link AuditApplicationListener} wired with all {@link EventTransformer} beans found in the application
 * context, an {@link AuditEventContextResolver}, an {@link ApplicationName} and, if a version can be determined, an
 * {@link ApplicationVersion} bean, together with the {@link ApplicationReadyEventTransformer} and
 * {@link ContextClosedEventTransformer} that audit the application lifecycle. All of them are only created if the
 * application has not already declared beans of these types.
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
   * The resolver is set up with the {@link ApplicationName} bean, the {@link ApplicationVersion} bean if one was
   * created, and with the default principal configured via
   * {@code audit.default-principal}, i.e., the principal that the resolved context reports when no user is
   * authenticated. If this property is not assigned, the context reports no principal at all. A suggested value is
   * {@link AuditEvent#SYSTEM_PRINCIPAL}.
   * </p>
   *
   * @param applicationName the application name
   * @param applicationVersion provider for the application version, which may be absent
   * @param properties the audit support properties
   * @return an {@link AuditEventContextResolver}
   */
  @Bean
  @ConditionalOnMissingBean
  @NonNull AuditEventContextResolver auditEventContextResolver(final @NonNull ApplicationName applicationName,
      final @NonNull ObjectProvider<ApplicationVersion> applicationVersion,
      final @NonNull AuditSupportProperties properties) {
    final DefaultAuditEventContextResolver resolver =
        new DefaultAuditEventContextResolver(applicationName, applicationVersion.getIfAvailable());
    resolver.setDefaultPrincipal(properties.getDefaultPrincipal());
    return resolver;
  }

  /**
   * Creates the {@link ApplicationReadyEventTransformer} bean, so that the application start is audited.
   * <p>
   * The bean is not created if the application has declared an {@link ApplicationReadyEventTransformer} bean of its
   * own, or if {@code audit.log-lifecycle-events} is {@code false}.
   * </p>
   *
   * @return an {@link ApplicationReadyEventTransformer}
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = AuditSupportProperties.PREFIX, name = "log-lifecycle-events",
      havingValue = "true", matchIfMissing = true)
  @NonNull ApplicationReadyEventTransformer applicationReadyEventTransformer() {
    return new ApplicationReadyEventTransformer();
  }

  /**
   * Creates the {@link ContextClosedEventTransformer} bean, so that the application shutdown is audited.
   * <p>
   * The bean is not created if the application has declared a {@link ContextClosedEventTransformer} bean of its own,
   * or if {@code audit.log-lifecycle-events} is {@code false}.
   * </p>
   *
   * @return a {@link ContextClosedEventTransformer}
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = AuditSupportProperties.PREFIX, name = "log-lifecycle-events",
      havingValue = "true", matchIfMissing = true)
  @NonNull ContextClosedEventTransformer contextClosedEventTransformer() {
    return new ContextClosedEventTransformer();
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

  /**
   * Creates the {@link ApplicationVersion} bean.
   * <p>
   * The version is resolved from the first available of: the {@code audit.app-version} property, or the version from
   * the {@link BuildProperties} (if available). Unlike the application name, the version is optional - if it can not
   * be determined, no bean is created and the audit events simply carry no version.
   * </p>
   *
   * @param properties the audit support properties
   * @param buildProperties provider for the build properties, which may be absent
   * @return an {@link ApplicationVersion}
   */
  @Bean
  @ConditionalOnMissingBean
  @Conditional(OnApplicationVersionAvailableCondition.class)
  @NonNull ApplicationVersion applicationVersion(final @NonNull AuditSupportProperties properties,
      final @NonNull ObjectProvider<BuildProperties> buildProperties) {

    final String applicationVersion = Optional.ofNullable(properties.getAppVersion())
        .filter(StringUtils::hasText)
        .orElseGet(() -> Optional.ofNullable(buildProperties.getIfAvailable())
            .map(BuildProperties::getVersion)
            .orElse(null));

    // The condition above has already established that a version is available.
    return new ApplicationVersion(Objects.requireNonNull(applicationVersion, "applicationVersion must not be null"));
  }

  /**
   * Condition that matches when an application version can be determined, i.e., when the {@code audit.app-version}
   * property is assigned, or when a {@link BuildProperties} bean carrying a version is available.
   */
  static class OnApplicationVersionAvailableCondition extends SpringBootCondition {

    /** The name of the property holding the application version. */
    private static final String PROPERTY = AuditSupportProperties.PREFIX + ".app-version";

    /** {@inheritDoc} */
    @Override
    public @NonNull ConditionOutcome getMatchOutcome(final @NonNull ConditionContext context,
        final @NonNull AnnotatedTypeMetadata metadata) {

      final ConditionMessage.Builder message = ConditionMessage.forCondition("Application Version");

      if (StringUtils.hasText(context.getEnvironment().getProperty(PROPERTY))) {
        return ConditionOutcome.match(message.because(PROPERTY + " is assigned"));
      }
      final ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
      final BuildProperties build = beanFactory != null
          ? beanFactory.getBeanProvider(BuildProperties.class).getIfAvailable()
          : null;
      if (build != null && build.getVersion() != null) {
        return ConditionOutcome.match(message.because("a BuildProperties bean holding a version is available"));
      }
      return ConditionOutcome.noMatch(
          message.because("neither " + PROPERTY + " nor a build version is available"));
    }
  }

}
