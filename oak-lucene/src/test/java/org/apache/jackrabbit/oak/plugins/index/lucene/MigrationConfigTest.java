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
package org.apache.jackrabbit.oak.plugins.index.lucene;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationConfigTest {

    @Test
    public void testDefaultConfig() {
        MigrationConfig config = new MigrationConfig();
        assertFalse(config.isEnableMigration());
        assertTrue(config.isKeepLegacyUpdated());
    }

    @Test
    public void testEnableMigration() {
        MigrationConfig config = new MigrationConfig();
        config.setEnableMigration(true);
        assertTrue(config.isEnableMigration());
    }

    @Test
    public void testKeepLegacyUpdated() {
        MigrationConfig config = new MigrationConfig();
        config.setKeepLegacyUpdated(false);
        assertFalse(config.isKeepLegacyUpdated());
    }

    @Test
    public void testGetState() {
        MigrationConfig config = new MigrationConfig();

        // State 0: Default
        assertEquals(MigrationConfig.State.PRE_MIGRATION, config.getState());

        // State 1: Active migration
        config.setEnableMigration(true);
        assertEquals(MigrationConfig.State.ACTIVE_MIGRATION, config.getState());

        // State 2: Point of no return
        config.setKeepLegacyUpdated(false);
        assertEquals(MigrationConfig.State.POINT_OF_NO_RETURN, config.getState());
    }
}
