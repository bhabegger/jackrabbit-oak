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
package org.apache.jackrabbit.oak.plugins.index.luceneNg;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.index.luceneNg.directory.OakDirectory;
import org.apache.jackrabbit.oak.plugins.index.search.FieldNames;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import java.io.IOException;

/**
 * Minimal IndexEditor for Lucene 9 - Phase 1 implementation.
 * Handles basic indexing of node properties into Lucene.
 */
public class LuceneNgIndexEditor implements Editor {
    private static final Logger LOG = LoggerFactory.getLogger(LuceneNgIndexEditor.class);

    private final String path;
    private final NodeBuilder definition;
    private final NodeState root;
    private final IndexWriter indexWriter;
    private final boolean isRoot;

    /**
     * Creates a new LuceneNgIndexEditor (root editor with new IndexWriter).
     *
     * @param path the path being indexed
     * @param definition the index definition
     * @param root the root node state
     */
    public LuceneNgIndexEditor(@NotNull String path,
                             @NotNull NodeBuilder definition,
                             @NotNull NodeState root) throws IOException {
        this.path = path;
        this.definition = definition;
        this.root = root;
        this.isRoot = true;

        // Create OakDirectory for this index
        // Store index data under the definition node at :data, like legacy Lucene
        String indexName = getIndexName(definition);
        OakDirectory directory = new OakDirectory(definition, indexName, false);

        // Create IndexWriter with basic config
        IndexWriterConfig config = new IndexWriterConfig();
        this.indexWriter = new IndexWriter(directory, config);

        LOG.debug("Created LuceneNgIndexEditor for path: {}", path);
    }

    /**
     * Creates a child LuceneNgIndexEditor that shares the parent's IndexWriter.
     *
     * @param path the path being indexed
     * @param definition the index definition
     * @param root the root node state
     * @param sharedWriter the shared IndexWriter from the parent
     */
    private LuceneNgIndexEditor(@NotNull String path,
                                @NotNull NodeBuilder definition,
                                @NotNull NodeState root,
                                @NotNull IndexWriter sharedWriter) {
        this.path = path;
        this.definition = definition;
        this.root = root;
        this.indexWriter = sharedWriter;
        this.isRoot = false;

        LOG.debug("Created child LuceneNgIndexEditor for path: {}", path);
    }

    @Override
    public void enter(@NotNull NodeState before, @NotNull NodeState after)
            throws CommitFailedException {
        // Node is being visited - index its properties if it should be indexed
        if (shouldIndex(path)) {
            try {
                indexNode(after);
            } catch (IOException e) {
                throw new CommitFailedException("Lucene9", 1,
                        "Failed to index node at " + path, e);
            }
        }
    }

    @Override
    public void leave(@NotNull NodeState before, @NotNull NodeState after)
            throws CommitFailedException {
        // Leaving node - commit if this is the root editor
        if (isRoot) {
            try {
                indexWriter.commit();
                indexWriter.close();
                LOG.debug("Committed Lucene 9 index");
            } catch (IOException e) {
                throw new CommitFailedException("Lucene9", 2,
                        "Failed to commit index", e);
            }
        }
    }

    @Override
    public void propertyAdded(@NotNull PropertyState after)
            throws CommitFailedException {
        // Property added - will be indexed in enter()
    }

    @Override
    public void propertyChanged(@NotNull PropertyState before,
                               @NotNull PropertyState after)
            throws CommitFailedException {
        // Property changed - will be re-indexed in enter()
    }

    @Override
    public void propertyDeleted(@NotNull PropertyState before)
            throws CommitFailedException {
        // Property deleted - document needs update
        // TODO: Implement document deletion/update in future phase
    }

    @Override
    @Nullable
    public Editor childNodeAdded(@NotNull String name, @NotNull NodeState after)
            throws CommitFailedException {
        // Child node added - create child editor sharing our IndexWriter
        String childPath = buildChildPath(name);
        return new LuceneNgIndexEditor(childPath, definition, root, indexWriter);
    }

    @Override
    @Nullable
    public Editor childNodeChanged(@NotNull String name,
                                  @NotNull NodeState before,
                                  @NotNull NodeState after)
            throws CommitFailedException {
        // Child node changed - create child editor sharing our IndexWriter
        String childPath = buildChildPath(name);
        return new LuceneNgIndexEditor(childPath, definition, root, indexWriter);
    }

    private String buildChildPath(String name) {
        if (path.isEmpty() || path.equals("/")) {
            return "/" + name;
        } else {
            return path + "/" + name;
        }
    }

    @Override
    @Nullable
    public Editor childNodeDeleted(@NotNull String name, @NotNull NodeState before)
            throws CommitFailedException {
        // Child node deleted
        // TODO: Implement document deletion in future phase
        return null;
    }

    /**
     * Indexes a node's properties into Lucene.
     */
    private void indexNode(NodeState node) throws IOException {
        Document doc = new Document();

        // Add path as stored field
        doc.add(new StringField("path", path, Field.Store.YES));

        // Index all properties
        for (PropertyState prop : node.getProperties()) {
            String propName = prop.getName();

            // Skip hidden properties (start with ':')
            if (propName.startsWith(":")) {
                continue;
            }

            // Handle different property types
            switch (prop.getType().tag()) {
                case PropertyType.LONG:
                    if (!prop.isArray()) {
                        long value = prop.getValue(org.apache.jackrabbit.oak.api.Type.LONG);
                        doc.add(new LongPoint(propName, value));           // For range queries
                        doc.add(new StoredField(propName, value));         // For retrieval
                        doc.add(new NumericDocValuesField(propName, value)); // For sorting
                    }
                    break;

                case PropertyType.DOUBLE:
                    if (!prop.isArray()) {
                        double value = prop.getValue(org.apache.jackrabbit.oak.api.Type.DOUBLE);
                        doc.add(new DoublePoint(propName, value));                               // For range queries
                        doc.add(new StoredField(propName, value));                               // For retrieval
                        doc.add(new DoubleDocValuesField(propName, Double.doubleToRawLongBits(value))); // For sorting
                    }
                    break;

                case PropertyType.DATE:
                    if (!prop.isArray()) {
                        String dateStr = prop.getValue(org.apache.jackrabbit.oak.api.Type.DATE);
                        try {
                            long millis = org.apache.jackrabbit.util.ISO8601.parse(dateStr).getTimeInMillis();
                            doc.add(new LongPoint(propName, millis));           // For range queries
                            doc.add(new StoredField(propName, millis));         // For retrieval
                            doc.add(new NumericDocValuesField(propName, millis)); // For sorting
                        } catch (Exception e) {
                            LOG.error("Failed to parse date: " + dateStr, e);
                        }
                    }
                    break;

                case PropertyType.BOOLEAN:
                    if (!prop.isArray()) {
                        boolean value = prop.getValue(org.apache.jackrabbit.oak.api.Type.BOOLEAN);
                        String strValue = String.valueOf(value);
                        doc.add(new StringField(propName, strValue, Field.Store.NO));           // For queries
                        doc.add(new SortedDocValuesField(propName, new BytesRef(strValue)));   // For sorting
                    }
                    break;

                case PropertyType.STRING:
                    if (!prop.isArray()) {
                        String value = prop.getValue(org.apache.jackrabbit.oak.api.Type.STRING);
                        if (value.length() < 32000) {
                            doc.add(new StringField(propName, value, Field.Store.NO));           // For queries
                            doc.add(new SortedDocValuesField(propName, new BytesRef(value)));   // For sorting
                        }
                        doc.add(new TextField(FieldNames.FULLTEXT, value, Field.Store.NO));
                        LOG.trace("Indexed property: {} = {}", propName, value);
                    } else {
                        // Multi-value string properties
                        for (String strValue : prop.getValue(org.apache.jackrabbit.oak.api.Type.STRINGS)) {
                            if (strValue.length() < 32000) {
                                doc.add(new StringField(propName, strValue, Field.Store.NO));
                                // Note: SortedDocValuesField only supports single value, skipping for multi-value
                            }
                            doc.add(new TextField(FieldNames.FULLTEXT, strValue, Field.Store.NO));
                        }
                    }
                    break;
            }
        }

        // Only add document if it has indexed fields
        if (doc.getFields().size() > 1) { // More than just path field
            indexWriter.addDocument(doc);
            LOG.debug("Indexed node at path: {}", path);
        }
    }

    private String getIndexName(NodeBuilder definition) {
        // Get index name from definition or use default
        return definition.hasProperty("name")
                ? definition.getString("name")
                : "lucene9-index";
    }

    /**
     * Determines if a node at the given path should be indexed.
     * Filters out system paths and index definitions.
     */
    private boolean shouldIndex(String nodePath) {
        // Skip root node
        if (nodePath.isEmpty() || nodePath.equals("/") || nodePath.equals("//")) {
            return false;
        }

        // Skip /oak:index/* (index definitions)
        if (nodePath.startsWith("/oak:index") || nodePath.startsWith("//oak:index")) {
            return false;
        }

        // Skip /jcr:system/* (system nodes)
        if (nodePath.startsWith("/jcr:system") || nodePath.startsWith("//jcr:system")) {
            return false;
        }

        // Index everything else
        return true;
    }
}
