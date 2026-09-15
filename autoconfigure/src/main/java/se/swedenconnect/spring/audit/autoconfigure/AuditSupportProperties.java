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

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Spring Audit support.
 *
 * @author Martin Lindström
 */
@ConfigurationProperties(prefix = AuditSupportProperties.PREFIX)
public class AuditSupportProperties {

  /** The configuration properties prefix. */
  public static final String PREFIX = "audit";

  /**
   * Whether the application lifecycle should be audited, i.e., whether a system_started event should be created when
   * the application has started, and a system_shutdown event when it is shutting down. The default is true.
   */
  private boolean logLifecycleEvents = true;

  /**
   * The version of the application, included in every audit event. Optional. If not assigned, the version of the build
   * information is used, if available. If neither is available, the audit events carry no application version.
   */
  private @Nullable String appVersion;

  /**
   * The principal name offered to the audit event transformers when no user is authenticated. If not assigned, no
   * principal is offered in these cases, but a transformer may still assign a principal of its own to the audit events
   * it creates. A commonly used value is "system".
   */
  private @Nullable String defaultPrincipal;

  /**
   * Tells whether the application lifecycle should be audited.
   *
   * @return whether the application lifecycle should be audited
   */
  public boolean isLogLifecycleEvents() {
    return this.logLifecycleEvents;
  }

  /**
   * Assigns whether the application lifecycle should be audited, i.e., whether a system_started event should be
   * created when the application has started, and a system_shutdown event when it is shutting down. The default is
   * true.
   *
   * @param logLifecycleEvents whether the application lifecycle should be audited
   */
  public void setLogLifecycleEvents(final boolean logLifecycleEvents) {
    this.logLifecycleEvents = logLifecycleEvents;
  }

  /**
   * Gets the version of the application.
   *
   * @return the application version, or {@code null} if none has been assigned
   */
  public @Nullable String getAppVersion() {
    return this.appVersion;
  }

  /**
   * Assigns the version of the application, i.e., the version included in every audit event. If not assigned, the
   * version of a {@link org.springframework.boot.info.BuildProperties BuildProperties} bean is used, if available. If
   * neither is available, the audit events carry no application version.
   *
   * @param appVersion the application version
   */
  public void setAppVersion(final @Nullable String appVersion) {
    this.appVersion = appVersion;
  }

  /**
   * Gets the principal name offered to the audit event transformers when no user is authenticated.
   *
   * @return the default principal name, or {@code null} if none has been assigned
   */
  public @Nullable String getDefaultPrincipal() {
    return this.defaultPrincipal;
  }

  /**
   * Assigns the principal name that should be offered to the audit event transformers when no user is authenticated.
   * If not assigned, no principal is offered in these cases, but a transformer may still assign a principal of its own
   * to the audit events it creates. A commonly used value is "system".
   *
   * @param defaultPrincipal the default principal name
   */
  public void setDefaultPrincipal(final @Nullable String defaultPrincipal) {
    this.defaultPrincipal = defaultPrincipal;
  }

}
