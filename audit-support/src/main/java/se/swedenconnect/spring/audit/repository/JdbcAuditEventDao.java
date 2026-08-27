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
package se.swedenconnect.spring.audit.repository;

/**
 * A marker interface for {@link AuditEventDao} implementations backed by a relational database (JDBC).
 * <p>
 * The marker allows the auto-configuration to distinguish a JDBC data access object from a
 * {@link MongoAuditEventDao MongoDB} one. A custom JDBC data access object that should be picked up by the
 * auto-configuration should implement this interface (rather than the bare {@link AuditEventDao}).
 * </p>
 *
 * @author Martin Lindström
 */
public interface JdbcAuditEventDao extends AuditEventDao {
}
