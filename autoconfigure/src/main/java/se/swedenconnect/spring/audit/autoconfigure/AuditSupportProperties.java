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
   * The principal name offered to the audit event transformers when no user is authenticated. If not assigned, no
   * principal is offered in these cases, but a transformer may still assign a principal of its own to the audit events
   * it creates. A commonly used value is "system".
   */
  private @Nullable String defaultPrincipal;

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
