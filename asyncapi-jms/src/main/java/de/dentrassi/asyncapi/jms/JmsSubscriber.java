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

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.asyncapi.ListenerHandle;
import de.dentrassi.asyncapi.Subscribe;

public class JmsSubscriber<T extends de.dentrassi.asyncapi.Message<P>, P extends Serializable> implements Subscribe<T> {

    private static final Logger logger = LoggerFactory.getLogger(JmsSubscriber.class);

    private class HandleImpl extends CompletableFuture<Void> implements ListenerHandle {

        private Session session;
        private MessageConsumer consumer;
        private final Consumer<T> handler;

        public HandleImpl(final Consumer<T> consumer) {
            this.handler = consumer;
        }

        @Override
        public void close() throws Exception {

            try {
                get();
            } catch (final Exception e) {
                // ignore
            }

            LinkedList<Exception> errors = null;

            if (this.consumer != null) {
                try {
                    this.consumer.close();
                } catch (final Exception e) {
                    if (errors == null) {
                        errors = new LinkedList<>();
                    }
                    errors.add(e);
                }
            }
            if (this.session != null) {
                try {
                    this.session.close();
                } catch (final Exception e) {
                    if (errors == null) {
                        errors = new LinkedList<>();
                    }
                    errors.add(e);
                }
            }

            if (errors != null && !errors.isEmpty()) {
                final Exception e = errors.pollFirst();
                errors.stream().forEach(e::addSuppressed);
                throw e;
            }
        }

        public void subscribe() {
            try {
                this.session = JmsSubscriber.this.connection.createSession(Session.CLIENT_ACKNOWLEDGE);
                final Destination destination = this.session.createTopic(JmsSubscriber.this.topic);
                this.consumer = this.session.createConsumer(destination);
                this.consumer.setMessageListener(this::processMessage);

                complete(null);
            } catch (final Exception e) {
                completeExceptionally(e);
            }
        }

        protected void processMessage(final Message message) {
            logger.debug("Received message: {}", message);

            try {
                final T m = JmsSubscriber.this.payloadFormat.decode(JmsSubscriber.this.clazz, JmsSubscriber.this.payloadClazz, message);
                if (m != null) {
                    this.handler.accept(m);
                }
                message.acknowledge();
            } catch (final Exception e) {
                logger.debug("Failed to handle message", e);
                try {
                    this.session.recover();
                } catch (final JMSException e1) {
                    // FIXME: we need to handle this somehow
                    // possible solution, re-create session
                }
            }
        }

    };

    private final Class<T> clazz;
    private final Class<P> payloadClazz;
    private final JmsPayloadFormat payloadFormat;
    private final String topic;
    private final Connection connection;
    private final Executor executor;

    public JmsSubscriber(final Class<T> clazz, final Class<P> payloadClazz, final JmsPayloadFormat payloadFormat, final String topic, final Connection connection,
            final Executor executor) {
        this.clazz = clazz;
        this.payloadClazz = payloadClazz;
        this.payloadFormat = payloadFormat;
        this.topic = topic;
        this.connection = connection;
        this.executor = executor;
    }

    @Override
    public ListenerHandle subscribe(final Consumer<T> consumer) {
        Objects.requireNonNull(consumer);

        final HandleImpl handle = new HandleImpl(consumer);

        this.executor.execute(() -> {
            handle.subscribe();
        });

        return handle;
    }

}
