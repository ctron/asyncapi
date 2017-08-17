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
import java.util.Objects;

import javax.jms.Session;
import javax.jms.TextMessage;

import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.format.TextPayloadFormat;

public interface JmsPayloadFormat {

    public <M extends Message<P>, P extends Serializable> M decode(Class<M> clazz, Class<P> payloadClazz, javax.jms.Message message) throws Exception;

    public javax.jms.Message encode(Session session, Message<?> message) throws Exception;

    public static JmsPayloadFormat textMessageFormat(final TextPayloadFormat textPayloadFormat) {
        Objects.requireNonNull(textPayloadFormat);

        return new JmsPayloadFormat() {

            @Override
            public javax.jms.Message encode(final Session session, final Message<?> message) throws Exception {
                return session.createTextMessage(textPayloadFormat.encode(message));
            }

            @Override
            public <M extends Message<P>, P extends Serializable> M decode(final Class<M> clazz, final Class<P> payloadClazz, final javax.jms.Message message) throws Exception {
                if (message instanceof TextMessage) {
                    return textPayloadFormat.decode(clazz, payloadClazz, ((TextMessage) message).getText());
                }
                return null;
            }
        };
    }

    public static JmsPayloadFormat objectMessageFormat() {
        return new JmsPayloadFormat() {

            @Override
            public javax.jms.Message encode(final Session session, final Message<?> message) throws Exception {
                return session.createObjectMessage(message.getPayload());
            }

            @Override
            public <M extends Message<P>, P extends Serializable> M decode(final Class<M> clazz, final Class<P> payloadClazz, final javax.jms.Message message) throws Exception {
                if (message.isBodyAssignableTo(payloadClazz)) {

                    final M m = clazz.newInstance();
                    m.setPayload(message.getBody(payloadClazz));

                    return m;
                }
                return null;
            }
        };

    }
}
