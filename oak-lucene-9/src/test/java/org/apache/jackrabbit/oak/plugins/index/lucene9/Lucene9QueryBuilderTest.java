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
package org.apache.jackrabbit.oak.plugins.index.lucene9;

import org.apache.jackrabbit.oak.plugins.index.search.spi.Query;
import org.apache.jackrabbit.oak.plugins.index.search.spi.QueryBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class Lucene9QueryBuilderTest {

    @Test
    public void testTermQuery() {
        QueryBuilder builder = new Lucene9QueryBuilder();
        Query query = builder.term("path", "/content");
        assertNotNull(query);
        assertTrue(query instanceof Lucene9Query);
    }

    @Test
    public void testRangeQuery() {
        QueryBuilder builder = new Lucene9QueryBuilder();
        Query query = builder.range("size", "100", "1000", true, false);
        assertNotNull(query);
        assertTrue(query instanceof Lucene9Query);
    }

    @Test
    public void testWildcardQuery() {
        QueryBuilder builder = new Lucene9QueryBuilder();
        Query query = builder.wildcard("name", "test*");
        assertNotNull(query);
        assertTrue(query instanceof Lucene9Query);
    }

    @Test
    public void testPrefixQuery() {
        QueryBuilder builder = new Lucene9QueryBuilder();
        Query query = builder.prefix("path", "/content/");
        assertNotNull(query);
        assertTrue(query instanceof Lucene9Query);
    }
}
