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

package de.dentrassi.asyncapi.gson.time;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class DateTimeAdapterFactory implements TypeAdapterFactory {

    private static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private static class TemporalAccessorTypeAdapter<T extends TemporalAccessor> extends TypeAdapter<T> {

        private final DateTimeFormatter formatter;
        private final TemporalQuery<T> query;
        private final Function<T, ? extends TemporalAccessor> whenWriting;

        public TemporalAccessorTypeAdapter(final DateTimeFormatter formatter, final TemporalQuery<T> query, final Function<T, ? extends TemporalAccessor> whenWriting) {
            this.formatter = formatter;
            this.query = query;
            this.whenWriting = whenWriting;
        }

        @Override
        public void write(final JsonWriter out, final T value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(this.formatter.format(this.whenWriting.apply(value)));
            }
        }

        @Override
        public T read(final JsonReader in) throws IOException {
            final JsonToken next = in.peek();

            switch (next) {
            case STRING: {
                final String value = in.nextString();
                return this.formatter.parse(value, this.query);
            }
            case NULL:
                in.nextNull();
                return null;
            default:
                throw new JsonSyntaxException("Invalid content for timestamp");
            }
        }

    }

    private final DateTimeFormatter formatter;
    private final Function<TemporalAccessor, ? extends TemporalAccessor> whenWriting;

    public DateTimeAdapterFactory(final DateTimeFormatter formatter, final Function<TemporalAccessor, ? extends TemporalAccessor> whenWriting) {
        this.formatter = formatter;
        this.whenWriting = whenWriting;
    }

    @Override
    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> token) {

        final Class<? super T> clazz = token.getRawType();

        if (Instant.class.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            final TypeAdapter<T> result = (TypeAdapter<T>) new TemporalAccessorTypeAdapter<>(this.formatter, Instant::from, this.whenWriting);
            return result;
        } else if (ZonedDateTime.class.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            final TypeAdapter<T> result = (TypeAdapter<T>) new TemporalAccessorTypeAdapter<>(this.formatter, ZonedDateTime::from, this.whenWriting);
            return result;
        }

        return null;
    }

    public static Consumer<GsonBuilder> iso8601() {
        return builder -> {
            builder.registerTypeAdapterFactory(new DateTimeAdapterFactory(DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC), value -> value));
            builder.setDateFormat(ISO_8601_DATE_FORMAT);
        };
    }

    public static Consumer<GsonBuilder> iso8601WithTimezone() {
        return builder -> {
            builder.registerTypeAdapterFactory(new DateTimeAdapterFactory(DateTimeFormatter.ISO_DATE_TIME, DateTimeAdapterFactory::makeZoned));
            builder.setDateFormat(ISO_8601_DATE_FORMAT);
        };
    }

    public static TemporalAccessor makeZoned(final TemporalAccessor value) {
        if (value == null) {
            return null;
        }

        if (value.isSupported(ChronoField.YEAR)) {
            return value;
        }

        if (value instanceof Instant) {
            return ((Instant) value).atOffset(ZoneOffset.UTC);
        }

        throw new IllegalArgumentException("Unable to process date time type: " + value.getClass());
    }

}
