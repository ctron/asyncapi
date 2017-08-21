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

import de.dentrassi.asyncapi.MessageReference;
import de.dentrassi.asyncapi.Topic;

public enum ConnectorType {
    CLIENT {
        @Override
        public String getPackageName() {
            return "client";
        }

        @Override
        public String getSimpleTypeName() {
            return "Client";
        }

        @Override
        public MessageReference getPublish(final Topic topic) {
            return topic.getPublish();
        }

        @Override
        public MessageReference getSubscribe(final Topic topic) {
            return topic.getSubscribe();
        }
    },
    SERVER {

        @Override
        public String getPackageName() {
            return "server";
        }

        @Override
        public String getSimpleTypeName() {
            return "Server";
        }

        @Override
        public MessageReference getPublish(final Topic topic) {
            return topic.getSubscribe(); // swap PUB <-> SUB for server
        }

        @Override
        public MessageReference getSubscribe(final Topic topic) {
            return topic.getPublish(); // swap PUB <-> SUB for server
        }
    };

    public abstract String getPackageName();

    public abstract String getSimpleTypeName();

    public abstract MessageReference getPublish(Topic topic);

    public abstract MessageReference getSubscribe(Topic topic);

}
