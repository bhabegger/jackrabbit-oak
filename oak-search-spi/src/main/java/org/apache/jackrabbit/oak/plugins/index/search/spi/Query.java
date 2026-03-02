/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.index.search.spi;

/**
 * Marker interface for queries.
 *
 * <p>Implementations are version-specific wrappers around
 * Lucene Query objects. This interface provides type safety
 * without exposing Lucene internals.</p>
 *
 * <p>Use {@link QueryBuilder} to create queries.</p>
 */
public interface Query {
    // Marker interface - actual query logic in implementations
}
