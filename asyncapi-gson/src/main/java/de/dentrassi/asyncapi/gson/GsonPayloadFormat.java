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

package de.dentrassi.asyncapi.gson;

import java.io.Serializable;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.format.TextPayloadFormat;
import de.dentrassi.asyncapi.gson.time.DateTimeAdapterFactory;
import de.dentrassi.asyncapi.gson.time.DateTimeStrategy;

public class GsonPayloadFormat implements TextPayloadFormat {

    private final Gson gson;

    public GsonPayloadFormat() {
        this(DateTimeStrategy.ISO_8601_UTC);
    }

    public GsonPayloadFormat(final DateTimeStrategy dateTimeStrategy) {
        Objects.requireNonNull(dateTimeStrategy);

        final GsonBuilder builder = new GsonBuilder();

        switch (dateTimeStrategy) {

        case ISO_8601_UTC:
            DateTimeAdapterFactory.iso8601().accept(builder);
            break;

        case ISO_8601_ZONED:
            DateTimeAdapterFactory.iso8601WithTimezone().accept(builder);
            break;

        default:
            throw new IllegalArgumentException("Unknown date time strategy: " + dateTimeStrategy);
        }

        this.gson = builder.create();
    }

    public GsonPayloadFormat(final GsonBuilder builder) {
        this.gson = builder.create();
    }

    @Override
    public String encode(final Message<?> message) throws Exception {
        return this.gson.toJson(message.getPayload());
    }

    @Override
    public <M extends Message<P>, P extends Serializable> M decode(final Class<M> clazz, final Class<P> payloadClazz, final String message) throws Exception {
        final M m = clazz.newInstance();
        m.setPayload(this.gson.fromJson(message, payloadClazz));
        return m;
    }

}
