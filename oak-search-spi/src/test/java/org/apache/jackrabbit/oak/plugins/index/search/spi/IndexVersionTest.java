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

import org.junit.Test;
import static org.junit.Assert.*;

public class IndexVersionTest {

    @Test
    public void testLucene47IsLegacy() {
        assertTrue(IndexVersion.LUCENE_4_7_2.isLegacy());
        assertFalse(IndexVersion.LUCENE_4_7_2.isModern());
    }

    @Test
    public void testLucene9IsModern() {
        assertFalse(IndexVersion.LUCENE_9_X.isLegacy());
        assertTrue(IndexVersion.LUCENE_9_X.isModern());
    }

    @Test
    public void testVersionComparison() {
        assertTrue(IndexVersion.LUCENE_4_7_2.olderThan(IndexVersion.LUCENE_9_X));
        assertFalse(IndexVersion.LUCENE_9_X.olderThan(IndexVersion.LUCENE_4_7_2));
    }

    @Test
    public void testVersionString() {
        assertEquals("4.7.2", IndexVersion.LUCENE_4_7_2.toString());
        assertEquals("9.x", IndexVersion.LUCENE_9_X.toString());
    }
}
