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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Lucene9IndexConstantsTest {

    @Test
    public void testTypeConstant() {
        assertNotNull(Lucene9IndexConstants.TYPE_LUCENE9);
        assertEquals("lucene9", Lucene9IndexConstants.TYPE_LUCENE9);
    }

    @Test
    public void testStoragePathConstant() {
        assertNotNull(Lucene9IndexConstants.VAR_INDEXING_BASE_PATH);
        assertEquals("/var/indexing/lucene9", Lucene9IndexConstants.VAR_INDEXING_BASE_PATH);
    }

    @Test
    public void testDirListingProperty() {
        assertNotNull(Lucene9IndexConstants.PROP_DIR_LISTING);
        assertEquals("dirListing", Lucene9IndexConstants.PROP_DIR_LISTING);
    }

    @Test
    public void testBlobSizeProperty() {
        assertNotNull(Lucene9IndexConstants.PROP_BLOB_SIZE);
        assertEquals("blobSize", Lucene9IndexConstants.PROP_BLOB_SIZE);
    }
}
