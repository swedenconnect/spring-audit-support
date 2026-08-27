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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import se.swedenconnect.spring.audit.AuditApplicationListener;
import se.swedenconnect.spring.audit.AuditEvent;
import se.swedenconnect.spring.audit.AuditEventBuilder;
import se.swedenconnect.spring.audit.AuditEventContext;
import se.swedenconnect.spring.audit.AuditEventContextResolver;
import se.swedenconnect.spring.audit.DefaultAuditEventContextResolver;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.tracing.CorrelationID;
import se.swedenconnect.spring.audit.tracing.TraceID;
import se.swedenconnect.spring.audit.transform.EventTransformer;

import java.io.Serial;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link AuditSupportAutoConfiguration}.
 *
 * @author Martin Lindström
 */
class AuditSupportAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AuditSupportAutoConfiguration.class))
      .withPropertyValues("spring.application.name=test-app");

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void testDefaultBeans() {
    this.runner.run(context -> {
      assertThat(context).hasSingleBean(ApplicationName.class);
      assertThat(context).hasSingleBean(AuditEventContextResolver.class);
      assertThat(context).hasSingleBean(AuditApplicationListener.class);
      assertThat(context.getBean(ApplicationName.class)).isEqualTo(new ApplicationName("test-app"));
    });
  }

  @Test
  void testDefaultContextResolverIsCreated() {
    this.runner.run(context -> assertThat(context.getBean(AuditEventContextResolver.class))
        .isInstanceOf(DefaultAuditEventContextResolver.class));
  }

  @Test
  void testDefaultContextResolverIsSetUpWithApplicationName() {
    this.runner.run(context -> {
      final AuditEventContext auditContext = context.getBean(AuditEventContextResolver.class).getContext(null);
      assertThat(auditContext.getApplicationName()).isEqualTo(new ApplicationName("test-app"));
    });
  }

  @Test
  void testNoDefaultPrincipalIsConfigured() {
    this.runner.run(context -> {
      final AuditEventContext auditContext = context.getBean(AuditEventContextResolver.class).getContext(null);
      assertThat(auditContext.getPrincipal()).isNull();
    });
  }

  @Test
  void testConfiguredDefaultPrincipalIsUsed() {
    this.runner.withPropertyValues("audit.default-principal=" + AuditEvent.SYSTEM_PRINCIPAL).run(context -> {
      assertThat(context.getBean(AuditSupportProperties.class).getDefaultPrincipal())
          .isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);

      final AuditEventContext auditContext = context.getBean(AuditEventContextResolver.class).getContext(null);
      assertThat(auditContext.getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
    });
  }

  @Test
  void testConfiguredDefaultPrincipalIsNotUsedWhenUserIsAuthenticated() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));

    this.runner.withPropertyValues("audit.default-principal=" + AuditEvent.SYSTEM_PRINCIPAL).run(context -> {
      final AuditEventContext auditContext = context.getBean(AuditEventContextResolver.class).getContext(null);
      assertThat(auditContext.getPrincipal()).isEqualTo("alice");
    });
  }

  @Test
  void testDefaultContextResolverResolvesAuthenticatedUser() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));

    this.runner.run(context -> {
      final AuditEventContext auditContext = context.getBean(AuditEventContextResolver.class).getContext(null);
      assertThat(auditContext.getPrincipal()).isEqualTo("alice");
    });
  }

  @Test
  void testContextResolverBacksOffWhenUserBeanIsPresent() {
    final AuditEventContextResolver userResolver = new TestContextResolver();

    this.runner.withBean(AuditEventContextResolver.class, () -> userResolver).run(context -> {
      assertThat(context).hasSingleBean(AuditEventContextResolver.class);
      assertThat(context.getBean(AuditEventContextResolver.class)).isSameAs(userResolver);
      assertThat(context).doesNotHaveBean(DefaultAuditEventContextResolver.class);
    });
  }

  @Test
  void testListenerBacksOffWhenUserBeanIsPresent() {
    final AuditApplicationListener userListener = new AuditApplicationListener(event -> {
    }, new TestContextResolver(), List.of());

    this.runner.withBean(AuditApplicationListener.class, () -> userListener).run(context -> {
      assertThat(context).hasSingleBean(AuditApplicationListener.class);
      assertThat(context.getBean(AuditApplicationListener.class)).isSameAs(userListener);
    });
  }

  @Test
  void testListenerIsWiredWithTheContextResolverBean() {
    this.runner
        .withBean(AuditEventContextResolver.class, TestContextResolver::new)
        .withBean(EventTransformer.class, RecordingEventTransformer::new)
        .run(context -> {
          final TestEvent event = new TestEvent("src");
          context.publishEvent(event);

          final RecordingEventTransformer transformer =
              (RecordingEventTransformer) context.getBean(EventTransformer.class);
          assertThat(transformer.principal).isEqualTo("test-principal");

          final TestContextResolver resolver = (TestContextResolver) context.getBean(AuditEventContextResolver.class);
          assertThat(resolver.input).isSameAs(event);
        });
  }

  @Test
  void testApplicationNameBacksOffWhenUserBeanIsPresent() {
    final ApplicationName userApplicationName = new ApplicationName("my-app");

    this.runner.withBean(ApplicationName.class, () -> userApplicationName).run(context -> {
      assertThat(context.getBean(ApplicationName.class)).isSameAs(userApplicationName);
      assertThat(context.getBean(AuditEventContextResolver.class).getContext(null).getApplicationName())
          .isSameAs(userApplicationName);
    });
  }

  @Test
  void testApplicationNameFromBuildProperties() {
    final Properties properties = new Properties();
    properties.setProperty("artifact", "build-app");

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AuditSupportAutoConfiguration.class))
        .withBean(BuildProperties.class, () -> new BuildProperties(properties))
        .run(context -> assertThat(context.getBean(ApplicationName.class))
            .isEqualTo(new ApplicationName("build-app")));
  }

  @Test
  void testNoApplicationNameAvailable() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AuditSupportAutoConfiguration.class))
        .run(context -> assertThat(context).hasFailed());
  }

  /**
   * A custom {@link AuditEventContextResolver} used to verify that the auto-configuration backs off. It records the
   * input it was given.
   */
  private static class TestContextResolver implements AuditEventContextResolver {

    private @Nullable Object input;

    @Override
    public @NonNull AuditEventContext getContext(final @Nullable Object input) {
      this.input = input;
      return new AuditEventContext() {

        @Override
        public @Nullable ApplicationName getApplicationName() {
          return null;
        }

        @Override
        public @Nullable CorrelationID getCorrelationId() {
          return null;
        }

        @Override
        public @Nullable TraceID getTraceId() {
          return null;
        }

        @Override
        public @Nullable String getPrincipal() {
          return "test-principal";
        }
      };
    }
  }

  /**
   * An {@link EventTransformer} that records the principal of the {@link AuditEventContext} it is given.
   */
  private static class RecordingEventTransformer implements EventTransformer {

    private @Nullable String principal;

    @Override
    public org.springframework.boot.actuate.audit.@NonNull AuditEvent transform(
        final @NonNull ApplicationEvent event, final @NonNull AuditEventContext context) {
      this.principal = context.getPrincipal();
      return AuditEventBuilder.builder(context)
          .type("test-type")
          .build();
    }

    @Override
    public boolean supports(final @NonNull ApplicationEvent event) {
      return event instanceof TestEvent;
    }
  }

  /**
   * A plain {@link ApplicationEvent} used in the tests.
   */
  private static class TestEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    TestEvent(final Object source) {
      super(source);
    }
  }

}
