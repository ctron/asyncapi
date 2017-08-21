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

package de.dentrassi.asyncapi.jms;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import de.dentrassi.asyncapi.Connector;
import de.dentrassi.asyncapi.format.TextPayloadFormat;

public class AbstractJmsConnector implements Connector, AutoCloseable {

    public static abstract class Builder<C extends AbstractJmsConnector> extends Connector.AbstractBuilder<Builder<C>, C> {

        private JmsProfile profile;
        private JmsPayloadFormat payloadFormat = JmsPayloadFormat.objectMessageFormat();

        private String username;
        private String password;

        protected Builder() {
        }

        @Override
        protected Builder<C> builder() {
            return this;
        }

        public Builder<C> profile(final JmsProfile profile) {
            this.profile = profile;
            return this;
        }

        public JmsProfile profile() {
            return this.profile;
        }

        public Builder<C> payloadFormat(final TextPayloadFormat payloadFormat) {
            if (payloadFormat != null) {
                this.payloadFormat = JmsPayloadFormat.textMessageFormat(payloadFormat);
            } else {
                this.payloadFormat = null;
            }
            return this;
        }

        public Builder<C> payloadFormat(final JmsPayloadFormat payloadFormat) {
            this.payloadFormat = payloadFormat;
            return this;
        }

        public JmsPayloadFormat payloadFormat() {
            return this.payloadFormat;
        }

        public Builder<C> username(final String username) {
            this.username = username;
            return this;
        }

        public String username() {
            return this.username;
        }

        public Builder<C> password(final String password) {
            this.password = password;
            return this;
        }

        public String password() {
            return this.password;
        }
    }

    protected final Connection connection;
    protected final ExecutorService executor;

    protected AbstractJmsConnector(final AbstractJmsConnector.Builder<?> builder) throws JMSException {

        Objects.requireNonNull(builder.profile(), "JMS profile is not set");

        final ConnectionFactory cf = builder.profile().connectionFactory().apply(builder);

        final String username = builder.username();
        final String password = builder.password();

        if (username != null) {
            this.connection = cf.createConnection(username, password);
        } else {
            this.connection = cf.createConnection();
        }

        this.connection.start();

        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void close() throws Exception {
        try {
            this.connection.close();
        } finally {
            this.executor.shutdown();
        }
    }

}