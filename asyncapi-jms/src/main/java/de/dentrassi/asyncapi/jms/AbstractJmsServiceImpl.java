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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.Publish;

public abstract class AbstractJmsServiceImpl {

    private final Connection connection;
    private final Executor executor;
    private final JmsPayloadFormat payloadFormat;
    private final Function<String, String> topicMapper;

    public AbstractJmsServiceImpl(final Connection connection, final Executor executor, final JmsPayloadFormat payloadFormat, final String baseTopic) {
        this.connection = connection;
        this.executor = executor;
        this.payloadFormat = payloadFormat;
        this.topicMapper = baseTopic == null || baseTopic.isEmpty() ? topic -> topic : topic -> baseTopic + "." + topic;
    }

    protected <T extends Message<?>> Publish<T> createPublisher(final String localTopicName) {
        return new Publish<T>() {

            @Override
            public CompletionStage<?> publish(final T message) {
                return publishMessage(fullTopic(localTopicName), message);
            }
        };
    }

    protected CompletionStage<?> publishMessage(final String topic, final Message<?> message) {

        final CompletableFuture<?> future = new CompletableFuture<>();

        this.executor.execute(() -> {
            try {
                processPublishMessage(topic, message);
                future.complete(null);
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    protected void processPublishMessage(final String topic, final Message<?> message) throws Exception {
        try (final Session session = this.connection.createSession()) {

            final Destination destination = session.createTopic(topic);

            final javax.jms.Message jmsMessage = this.payloadFormat.encode(session, message);

            try (final MessageProducer producer = session.createProducer(destination)) {
                producer.send(jmsMessage);
            }
        }
    }

    protected <M extends Message<P>, P extends Serializable> JmsSubscriber<M, P> createSubscriber(final String localTopicName, final Class<M> clazz, final Class<P> payloadClazz) {
        return new JmsSubscriber<>(clazz, payloadClazz, this.payloadFormat, fullTopic(localTopicName), this.connection, this.executor);
    }

    protected String fullTopic(final String topic) {
        return this.topicMapper.apply(topic);
    }

}
