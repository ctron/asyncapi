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

package de.dentrassi.asyncapi.jms.amqp;

import java.util.function.Function;

import javax.jms.ConnectionFactory;

import org.apache.qpid.jms.JmsConnectionFactory;

import de.dentrassi.asyncapi.jms.AbstractJmsConnector;
import de.dentrassi.asyncapi.jms.JmsProfile;

public class AmqpProfile implements JmsProfile {

    private static final int STANDARD_AMQP_PORT = 5672;
    public static final JmsProfile DEFAULT_PROFILE = new AmqpProfile(STANDARD_AMQP_PORT);

    public static class Builder {

        private int port;

        protected Builder() {
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public JmsProfile build() {
            return new AmqpProfile(this.port);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private final int port;

    public AmqpProfile(final int port) {
        this.port = port <= 0 ? STANDARD_AMQP_PORT : port;
    }

    @Override
    public Function<AbstractJmsConnector.Builder<?>, ConnectionFactory> connectionFactory() {
        return builder -> {

            final String host = builder.host();

            final JmsConnectionFactory cf = new JmsConnectionFactory();

            cf.setRemoteURI(String.format("amqp://%s:%s", host, this.port));

            return cf;
        };
    }

}
