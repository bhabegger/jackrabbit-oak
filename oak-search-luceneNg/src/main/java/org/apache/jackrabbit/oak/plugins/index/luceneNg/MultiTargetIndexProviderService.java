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

import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.MultiTargetIndexEditorProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

/**
 * OSGi service that registers {@link MultiTargetIndexEditorProvider} for
 * the {@code lucene-multi} index type.
 *
 * <p>Collects all {@link IndexEditorProvider} services tagged with
 * {@code leaf=true} (typically the lucene47 and lucene9 leaf providers)
 * and assembles them into a {@link MultiTargetIndexEditorProvider} that
 * dispatches writes to all {@code storeTargets} declared on the index
 * definition.</p>
 *
 * <p>Because {@link MultiTargetIndexEditorProvider} is configured to only
 * handle {@code type=lucene-multi}, there is no overlap with the individual
 * leaf providers — they see an unknown type and return {@code null}.</p>
 *
 * <h2>Migration workflow</h2>
 * <ol>
 *   <li>Start: {@code type=lucene47} — handled by the lucene47 leaf provider</li>
 *   <li>Transition: change to {@code type=lucene-multi, storeTargets=[lucene47,lucene9],
 *       activeTarget=lucene47} — this service handles both writes;
 *       queries still go to lucene47</li>
 *   <li>Flip: set {@code activeTarget=lucene9} — queries now served by lucene9</li>
 *   <li>Cleanup: change to {@code type=lucene9} — reverts to single-target</li>
 * </ol>
 */
@Component
public class MultiTargetIndexProviderService {

    private static final Logger LOG = LoggerFactory.getLogger(MultiTargetIndexProviderService.class);

    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.STATIC,
            policyOption = ReferencePolicyOption.GREEDY,
            target = "(leaf=true)"
    )
    private List<IndexEditorProvider> leafProviders = new ArrayList<>();

    private ServiceRegistration<?> registration;

    @Activate
    private void activate(BundleContext bundleContext) {
        if (leafProviders.isEmpty()) {
            LOG.warn("MultiTargetIndexProviderService activated with no leaf providers — " +
                     "multi-target writes will fail. Ensure at least one IndexEditorProvider " +
                     "with property leaf=true is registered.");
        }

        MultiTargetIndexEditorProvider provider = new MultiTargetIndexEditorProvider(
                LuceneNgIndexConstants.TYPE_LUCENE_MULTI,
                new ArrayList<>(leafProviders));

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("type", LuceneNgIndexConstants.TYPE_LUCENE_MULTI);

        registration = bundleContext.registerService(
                IndexEditorProvider.class.getName(), provider, props);

        LOG.info("Registered MultiTargetIndexEditorProvider for type '{}' with {} leaf provider(s)",
                LuceneNgIndexConstants.TYPE_LUCENE_MULTI, leafProviders.size());
    }

    @Deactivate
    private void deactivate() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        LOG.info("MultiTargetIndexProviderService deactivated");
    }
}
