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

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
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

  /** Logger. */
  private static final Logger log = LoggerFactory.getLogger(AuditSupportAutoConfiguration.class);

  /** The property holding the location of the build information file. Same as Spring Boot uses. */
  private static final String BUILD_INFO_LOCATION_PROPERTY = "spring.info.build.location";

  /** The default location of the build information file. */
  private static final String DEFAULT_BUILD_INFO_LOCATION = "classpath:META-INF/build-info.properties";

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
   * The version is resolved from the first available of: the {@code audit.app-version} property, the version from the
   * {@link BuildProperties} bean (if available), or the version from the build information file. Unlike the
   * application name, the version is optional - if it can not be determined, no bean is created and the audit events
   * simply carry no version.
   * </p>
   *
   * @param properties the audit support properties
   * @param buildProperties provider for the build properties, which may be absent
   * @param resourceLoader the resource loader used to locate the build information file
   * @return an {@link ApplicationVersion}
   */
  @Bean
  @ConditionalOnMissingBean
  @Conditional(OnApplicationVersionAvailableCondition.class)
  @NonNull ApplicationVersion applicationVersion(final @NonNull AuditSupportProperties properties,
      final @NonNull ObjectProvider<BuildProperties> buildProperties, final @NonNull ResourceLoader resourceLoader) {

    final String applicationVersion = Optional.ofNullable(properties.getAppVersion())
        .filter(StringUtils::hasText)
        .or(() -> Optional.ofNullable(buildProperties.getIfAvailable())
            .map(BuildProperties::getVersion)
            .filter(StringUtils::hasText))
        .orElseGet(() -> readBuildInfoVersion(this.environment, resourceLoader));

    // The condition above has already established that a version is available.
    return new ApplicationVersion(Objects.requireNonNull(applicationVersion, "applicationVersion must not be null"));
  }

  /**
   * Reads the version from the build information file, i.e., the file pointed out by {@code spring.info.build.location}
   * ({@code classpath:META-INF/build-info.properties} by default). This is the same file that Spring Boot creates the
   * {@link BuildProperties} bean from.
   *
   * @param environment the Spring environment
   * @param resourceLoader the resource loader used to locate the file
   * @return the version, or {@code null} if there is no build information file, or if it holds no version
   */
  static @Nullable String readBuildInfoVersion(final @NonNull Environment environment,
      final @NonNull ResourceLoader resourceLoader) {
    final Resource resource = resourceLoader.getResource(
        environment.getProperty(BUILD_INFO_LOCATION_PROPERTY, DEFAULT_BUILD_INFO_LOCATION));
    if (!resource.exists()) {
      return null;
    }
    try {
      final String version = PropertiesLoaderUtils.loadProperties(resource).getProperty("build.version");
      return StringUtils.hasText(version) ? version : null;
    }
    catch (final IOException e) {
      log.warn("Failed to read build information from {} - no application version from build information", resource,
          e);
      return null;
    }
  }

  /**
   * Condition that matches when an application version can be determined, i.e., when the {@code audit.app-version}
   * property is assigned, or when the build information file holds a version.
   * <p>
   * The condition reads the build information file instead of asking for the {@link BuildProperties} bean, since a
   * condition is evaluated before beans can be created.
   * </p>
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
      if (readBuildInfoVersion(context.getEnvironment(), context.getResourceLoader()) != null) {
        return ConditionOutcome.match(message.because("the build information holds a version"));
      }
      return ConditionOutcome.noMatch(
          message.because("neither " + PROPERTY + " nor a build version is available"));
    }
  }

}
