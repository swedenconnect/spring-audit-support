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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link DefaultAuditEventContextResolver}.
 *
 * @author Martin Lindström
 */
class DefaultAuditEventContextResolverTest {

  @AfterEach
  void clear() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void testApplicationName() {
    final ApplicationName applicationName = new ApplicationName("test-app");
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(applicationName);

    assertThat(resolver.getContext(null).getApplicationName()).isSameAs(applicationName);
  }

  @Test
  void testNoApplicationName() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getApplicationName()).isNull();
  }

  @Test
  void testCorrelationIdFromMdc() {
    CorrelationID.of("abc-123").mdcPut();
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getCorrelationId()).isEqualTo(CorrelationID.of("abc-123"));
  }

  @Test
  void testNoCorrelationIdInMdc() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getCorrelationId()).isNull();
  }

  @Test
  void testTraceIdIsAlwaysNull() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getTraceId()).isNull();
  }

  @Test
  void testPrincipalFromSecurityContext() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);

    assertThat(resolver.getContext(null).getPrincipal()).isEqualTo("alice");
  }

  @Test
  void testNoAuthenticationAndNoDefaultPrincipal() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getPrincipal()).isNull();
  }

  @Test
  void testNoAuthenticationGivesDefaultPrincipal() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);

    assertThat(resolver.getContext(null).getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
  }

  @Test
  void testUnauthenticatedAuthenticationGivesDefaultPrincipal() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.unauthenticated("alice", null));
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);

    assertThat(resolver.getContext(null).getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
  }

  @Test
  void testAnonymousAuthenticationGivesDefaultPrincipal() {
    SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    resolver.setDefaultPrincipal(AuditEvent.SYSTEM_PRINCIPAL);

    assertThat(resolver.getContext(null).getPrincipal()).isEqualTo(AuditEvent.SYSTEM_PRINCIPAL);
  }

  @Test
  void testAnonymousAuthenticationAndNoDefaultPrincipal() {
    SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);

    assertThat(resolver.getContext(null).getPrincipal()).isNull();
  }

  @Test
  void testInputIsIgnored() {
    CorrelationID.of("abc-123").mdcPut();
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    final AuditEventContext context = resolver.getContext("some-state-information");

    assertThat(context.getCorrelationId()).isEqualTo(CorrelationID.of("abc-123"));
    assertThat(context.getPrincipal()).isEqualTo("alice");
  }

  @Test
  void testContextIsResolvedWhenInvoked() {
    final DefaultAuditEventContextResolver resolver = new DefaultAuditEventContextResolver(null);
    final AuditEventContext context = resolver.getContext(null);

    assertThat(context.getCorrelationId()).isNull();
    assertThat(context.getPrincipal()).isNull();

    CorrelationID.of("abc-123").mdcPut();
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));

    assertThat(context.getCorrelationId()).isEqualTo(CorrelationID.of("abc-123"));
    assertThat(context.getPrincipal()).isEqualTo("alice");
  }

}
