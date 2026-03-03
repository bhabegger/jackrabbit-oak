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

import org.apache.jackrabbit.oak.plugins.index.search.spi.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class Lucene9DocumentTest {

    @Test
    public void testCreateEmptyDocument() {
        Document doc = new Lucene9Document();
        assertNotNull(doc);
    }

    @Test
    public void testWrapLuceneDocument() {
        org.apache.lucene.document.Document luceneDoc =
            new org.apache.lucene.document.Document();
        luceneDoc.add(new org.apache.lucene.document.StringField(
            "path", "/content", org.apache.lucene.document.Field.Store.YES));

        Lucene9Document oakDoc = new Lucene9Document(luceneDoc);
        assertNotNull(oakDoc);
        assertEquals(luceneDoc, oakDoc.getLuceneDocument());
    }
}
