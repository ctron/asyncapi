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

package de.dentrassi.asyncapi.format;

import java.io.Serializable;

import de.dentrassi.asyncapi.Message;

/**
 * A Text based payload format
 */
public interface TextPayloadFormat {
    /**
     * Encode a message into a text based format
     *
     * @param message
     *            the message to encode, must not be {@code null}
     * @return the encoded method, must not be {@code null}
     * @throws Exception
     *             if anything goes wrong
     */
    public String encode(Message<?> message) throws Exception;

    /**
     * Decode a message from a text based format
     *
     * @param clazz
     *            The expected message class
     * @param payloadClazz
     *            The expected payload class
     * @param message
     *            The text of the encoded messages, must not be {@code null}
     * @return The decoded message, must not be {@code null}
     * @throws Exception
     *             if anything goes wrong
     */
    public <M extends Message<P>, P extends Serializable> M decode(Class<M> clazz, Class<P> payloadClazz, String message) throws Exception;
}
