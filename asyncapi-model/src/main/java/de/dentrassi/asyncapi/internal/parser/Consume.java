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

package de.dentrassi.asyncapi.internal.parser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Consume {
    private Consume() {
    }

    public static Map<String, ?> asMap(final String key, final Map<String, ?> map) {
        return required(key, asOptionalMap(key, map));
    }

    @SuppressWarnings("unchecked")
    public static Optional<Map<String, ?>> asOptionalMap(final String key, final Map<String, ?> map) {
        final Object result = map.get(key);

        if (result == null) {
            return Optional.empty();
        }

        if (result instanceof Map) {
            return Optional.of((Map<String, ?>) result);
        }

        throw wrongTypeError(key, Map.class, result);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ?> asMap(final Object value) {
        if (value instanceof Map) {
            return (Map<String, ?>) value;
        }

        throw wrongTypeError(null, Map.class, value);
    }

    public static Optional<String> asOptionalString(final String key, final Map<String, ?> map) {
        final Object result = map.get(key);

        if (result == null) {
            return Optional.empty();
        }

        if (result instanceof String) {
            return Optional.ofNullable((String) result);
        }

        throw wrongTypeError(key, String.class, result);
    }

    public static String asString(final String key, final Map<String, ?> map) {
        return required(key, asOptionalString(key, map));
    }

    private static IllegalStateException wrongTypeError(final String key, final Class<?> expected, final Object result) {
        return new IllegalStateException(String.format("Key '%s' is expected to be of type %s (but is %s instead)", key != null ? " '" + key + "'" : "", expected.getSimpleName(),
                result.getClass().getSimpleName()));
    }

    private static IllegalStateException keyMissingError(final String key) {
        return new IllegalStateException(String.format("Key '%s' is missing in map", key));
    }

    private static <T> T required(final String key, final Optional<T> value) {
        return value.orElseThrow(() -> keyMissingError(key));
    }

    public static Set<String> asSet(final String key, final Map<String, ?> map) {
        return required(key, asOptionalSet(key, map));
    }

    @SuppressWarnings("unchecked")
    public static Optional<Set<String>> asOptionalSet(final String key, final Map<String, ?> map) {
        final Object value = map.get(key);

        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof Collection<?>) {
            return Optional.of(new LinkedHashSet<>((Collection<String>) value));
        }

        throw wrongTypeError(key, Collection.class, value);
    }

}
