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

/**
 * Oak Search SPI - Version-agnostic search abstraction layer.
 *
 * <p>This package provides interfaces that isolate Oak from specific
 * Lucene versions, enabling safe upgrades and clean architecture.</p>
 *
 * <h2>Key Interfaces:</h2>
 * <ul>
 *   <li>{@link IndexDirectory} - Directory abstraction</li>
 *   <li>{@link IndexReader} - Reader abstraction</li>
 *   <li>{@link IndexWriter} - Writer abstraction</li>
 *   <li>{@link QueryBuilder} - Query construction</li>
 *   <li>{@link DocumentBuilder} - Document creation</li>
 * </ul>
 *
 * <h2>Design Principle:</h2>
 * <p>Zero Lucene imports. All Lucene-specific types are hidden behind
 * these abstractions.</p>
 *
 * @since 1.66
 */
package org.apache.jackrabbit.oak.plugins.index.search.spi;
