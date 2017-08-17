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

package de.dentrassi.asyncapi.client;

public interface Client {

    public abstract class Builder<B extends Builder<B, C>, C extends Client> {
        private String host;
        private String baseTopic;

        protected Builder() {
        }

        public Builder<B, C> host(final String host) {
            this.host = host;
            return this;
        }

        public String host() {
            return this.host;
        }

        public Builder<B, C> baseTopic(final String baseTopic) {
            this.baseTopic = baseTopic;
            return this;
        }

        public String baseTopic() {
            return this.baseTopic;
        }

        public abstract C build();
    }
}
