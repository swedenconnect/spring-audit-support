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

/**
 * The identifiers that tie audit events together, and the pluggable storage they are kept in.
 * <p>
 * Two identifiers are supported, and the difference between them decides who assigns them:
 * </p>
 * <ul>
 *   <li>A {@link se.swedenconnect.spring.audit.tracing.CorrelationID correlation ID} may span several requests and is
 *   assigned by the application itself, based on its own logic - a request ID, an operation ID, or a case number. The
 *   application both reads and writes it, see
 *   {@link se.swedenconnect.spring.audit.tracing.CorrelationIDHolder CorrelationIDHolder}.</li>
 *   <li>A {@link se.swedenconnect.spring.audit.tracing.TraceID trace ID} lives within one request, but may span
 *   several services. It is assigned at the edge of a request, or by a tracing framework, so the application only
 *   reads it, see {@link se.swedenconnect.spring.audit.tracing.TraceIDHolder TraceIDHolder}.</li>
 * </ul>
 * <p>
 * Where the identifiers are kept is decided by an
 * {@link se.swedenconnect.spring.audit.tracing.IdentifierStorage IdentifierStorage}. Unless another implementation has
 * been installed, the identifiers are held in the SLF4J MDC, which is what a servlet based application needs. An
 * application that hops threads - a reactive application - installs a storage implementation that follows its own
 * context instead, see
 * {@link se.swedenconnect.spring.audit.tracing.IdentifierStorageHolder IdentifierStorageHolder}.
 * </p>
 */
package se.swedenconnect.spring.audit.tracing;
