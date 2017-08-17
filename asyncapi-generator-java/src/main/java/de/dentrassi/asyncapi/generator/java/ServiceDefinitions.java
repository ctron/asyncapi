/*
 * Copyright (C) 2017 Jens Reimann <jreimann@redhat.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dentrassi.asyncapi.generator.java;

import static java.util.Arrays.asList;
import static java.util.Optional.empty;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.generator.java.util.Version;

public class ServiceDefinitions {

    public static class VersionedService {
        private final TypeInformation type;
        private final Version version;

        public VersionedService(final TypeInformation type, final Version version) {
            this.type = type;
            this.version = version;
        }

        public TypeInformation getType() {
            return this.type;
        }

        public Version getVersion() {
            return this.version;
        }
    }

    private final Map<Topic, TopicInformation> topics;
    private final Map<String, Map<String, List<Topic>>> versions;
    private final Map<String, VersionedService> latest;

    public ServiceDefinitions(final Map<Topic, TopicInformation> topics, final Map<String, Map<String, List<Topic>>> versions, final Map<String, VersionedService> latest) {
        this.topics = Collections.unmodifiableMap(topics);
        this.versions = Collections.unmodifiableMap(versions); // FIXME: contained maps are still mutable
        this.latest = Collections.unmodifiableMap(latest);
    }

    public Map<Topic, TopicInformation> getTopics() {
        return this.topics;
    }

    public Map<String, Map<String, List<Topic>>> getVersions() {
        return this.versions;
    }

    public Map<String, VersionedService> getLatest() {
        return this.latest;
    }

    public static ServiceDefinitions build(final AsyncApi api, final boolean validateTopicSyntax) {

        final Map<Topic, TopicInformation> topics = new LinkedHashMap<>();
        final Map<String, Map<String, List<Topic>>> versions = new HashMap<>();

        for (final Topic topic : api.getTopics()) {

            TopicInformation ti;
            try {
                ti = TopicInformation.fromString(topic.getName());
            } catch (final IllegalArgumentException e) {
                if (validateTopicSyntax) {
                    throw e;
                }
                // fall back to default
                ti = new TopicInformation("Topics", "1", "event", new LinkedList<>(asList(topic.getName().split("\\."))), "send", empty());
            }

            addTopic(versions, ti, topic);
            topics.put(topic, ti);
        }

        final Map<String, VersionedService> latest = new HashMap<>();
        for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : versions.entrySet()) {
            for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {

                final TypeInformation serviceType = Generator.createServiceTypeInformation(serviceEntry);

                // record latest version

                final Version v = Version.valueOf(versionEntry.getKey());
                final VersionedService lv = latest.get(serviceType.getName());
                if (lv == null || v.compareTo(lv.getVersion()) > 0) {
                    latest.put(serviceType.getName(), new VersionedService(serviceType, v));
                }

            }
        }

        return new ServiceDefinitions(topics, versions, latest);

    }

    private static void addTopic(final Map<String, Map<String, List<Topic>>> versions, final TopicInformation ti, final Topic topic) {

        Map<String, List<Topic>> version = versions.get(ti.getVersion());
        if (version == null) {
            version = new HashMap<>();
            versions.put(ti.getVersion(), version);
        }

        List<Topic> service = version.get(ti.getService());
        if (service == null) {
            service = new LinkedList<>();
            version.put(ti.getService(), service);
        }

        service.add(topic);
    }
}
