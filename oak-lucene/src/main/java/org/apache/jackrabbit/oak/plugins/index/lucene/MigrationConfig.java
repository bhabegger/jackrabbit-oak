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

/**
 * Configuration for Lucene migration state machine.
 *
 * <p>Manages two toggles:</p>
 * <ul>
 *   <li>enableMigration - Start/stop migration process</li>
 *   <li>keepLegacyUpdated - Maintain Lucene 4.7 during/after migration</li>
 * </ul>
 */
public class MigrationConfig {

    /**
     * Migration states.
     */
    public enum State {
        /** State 0: Pre-migration (Lucene 4.7 only) */
        PRE_MIGRATION,

        /** State 1: Active migration (dual-write) */
        ACTIVE_MIGRATION,

        /** State 2: Point of no return (Lucene 9 only) */
        POINT_OF_NO_RETURN
    }

    private volatile boolean enableMigration = false;
    private volatile boolean keepLegacyUpdated = true;

    /**
     * Returns true if migration is enabled.
     */
    public boolean isEnableMigration() {
        return enableMigration;
    }

    /**
     * Enables or disables migration.
     *
     * @param enabled true to enable migration
     */
    public void setEnableMigration(boolean enabled) {
        this.enableMigration = enabled;
    }

    /**
     * Returns true if legacy (Lucene 4.7) should be kept updated.
     */
    public boolean isKeepLegacyUpdated() {
        return keepLegacyUpdated;
    }

    /**
     * Sets whether to keep legacy updated.
     *
     * @param keepUpdated true to keep legacy updated
     */
    public void setKeepLegacyUpdated(boolean keepUpdated) {
        this.keepLegacyUpdated = keepUpdated;
    }

    /**
     * Returns the current migration state.
     */
    public State getState() {
        if (!enableMigration) {
            return State.PRE_MIGRATION;
        }

        if (keepLegacyUpdated) {
            return State.ACTIVE_MIGRATION;
        }

        return State.POINT_OF_NO_RETURN;
    }
}
