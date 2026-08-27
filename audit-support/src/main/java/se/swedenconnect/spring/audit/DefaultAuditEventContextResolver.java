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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import se.swedenconnect.spring.audit.support.ApplicationName;
import se.swedenconnect.spring.audit.support.CorrelationID;

/**
 * Default implementation of the {@link AuditEventContextResolver}.
 * <p>
 * The default implementation resolves an {@link AuditEventContext} based on the following:
 * </p>
 * <ul>
 *   <li>{@link AuditEventContext#getApplicationName()} - Returns the configured application name.</li>
 *   <li>{@link AuditEventContext#getCorrelationId()} - Returns the CorrelationID currently stored in MDC ({@link CorrelationID#fromMDC()}), which can be {@code null}.</li>
 *   <li>{@link AuditEventContext#getTraceId()} - Always returns {@code null}.</li>
 *   <li>{@link AuditEventContext#getPrincipal()} - Gets the name of the currently authenticated user, i.e., the name of
 *   the {@link Authentication} object held by the current {@link SecurityContextHolder security context}. If there is no
 *   authenticated user, i.e., if there is no {@link Authentication} object, if it is not authenticated, or if it is an
 *   {@link AnonymousAuthenticationToken anonymous authentication}, the configured
 *   {@link #setDefaultPrincipal(String) default principal} is returned (which may be {@code null}).</li>
 * </ul>
 * <p>
 * All values are resolved from thread bound state, meaning that the {@code input} parameter of
 * {@link #getContext(Object)} is ignored by this implementation.
 * </p>
 *
 * @author Martin Lindström
 */
public class DefaultAuditEventContextResolver implements AuditEventContextResolver {

  /** The application name. */
  private final @Nullable ApplicationName applicationName;

  /**
   * The default principal to use if no principal can be found from the SecurityContextHolder when resolving the
   * principal.
   */
  private @Nullable String defaultPrincipal;

  /**
   * Constructor.
   *
   * @param applicationName the application name (or {@code null})
   */
  public DefaultAuditEventContextResolver(final @Nullable ApplicationName applicationName) {
    this.applicationName = applicationName;
  }

  /**
   * Returns an {@link AuditEventContext} holding the configured application name, the {@link CorrelationID} currently
   * stored in MDC, a {@code null} trace ID, and the name of the currently authenticated user (or the configured
   * {@link #setDefaultPrincipal(String) default principal} if there is no authenticated user).
   * <p>
   * The {@code input} parameter is not used by this implementation.
   * </p>
   *
   * @param input not used by this implementation
   * @return an {@link AuditEventContext}
   */
  @Override
  public @NonNull AuditEventContext getContext(final @Nullable Object input) {
    return new AuditEventContext() {

      @Override
      public @Nullable ApplicationName getApplicationName() {
        return DefaultAuditEventContextResolver.this.applicationName;
      }

      @Override
      public @Nullable CorrelationID getCorrelationId() {
        return CorrelationID.fromMDC();
      }

      @Override
      public @Nullable String getTraceId() {
        return null;
      }

      @Override
      public @Nullable String getPrincipal() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
          return DefaultAuditEventContextResolver.this.defaultPrincipal;
        }
        return authentication.getName();
      }

    };

  }

  /**
   * If the current {@link SecurityContextHolder security context} does not hold an authenticated principal it is
   * possible to configure a "default principal" to use. For example, this can be a system user, see
   * {@link AuditEvent#SYSTEM_PRINCIPAL}.
   *
   * @param defaultPrincipal principal name
   */
  public void setDefaultPrincipal(final @Nullable String defaultPrincipal) {
    this.defaultPrincipal = defaultPrincipal;
  }

}
