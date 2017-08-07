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

package de.dentrassi.asyncapi.parser;

import static de.dentrassi.asyncapi.AsyncApi.VERSION;

import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.EnumType;
import de.dentrassi.asyncapi.Information;
import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.MessageReference;
import de.dentrassi.asyncapi.ObjectType;
import de.dentrassi.asyncapi.Property;
import de.dentrassi.asyncapi.StringType;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.Type;
import de.dentrassi.asyncapi.TypeReference;

public class YamlParser {

    private final Map<String, ?> document;

    private final Map<String, Type> types = new HashMap<>();

    private final Map<String, Message> messages = new HashMap<>();

    public YamlParser(final InputStream in) {
        this.document = asMap(new Yaml().load(in));
    }

    public YamlParser(final Reader reader) {
        this.document = asMap(new Yaml().load(reader));
    }

    public AsyncApi parse() {
        final String version = asString("asyncapi", this.document);

        if (!VERSION.equals(version)) {
            throw new IllegalStateException(
                    String.format("Only version '%s' is supported, this is version '%s'", VERSION, version));
        }

        final AsyncApi api = new AsyncApi();

        api.setBaseTopic(asString("baseTopic", this.document));
        api.setHost(asString("host", this.document));
        api.setSchemes(asSet("schemes", this.document));
        api.setInformation(infoFromYaml(asMap("info", this.document)));
        api.setTopics(topicsFromYaml(asMap("topics", this.document)));

        final Map<String, ?> components = asMap("components", this.document);

        api.setMessages(messagesFromYaml(asOptionalMap("messages", components).orElse(null)));
        api.setTypes(typesFromYaml(asOptionalMap("schemas", components).orElse(null)));

        return api;
    }

    private Set<Type> typesFromYaml(final Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<Type> result = new LinkedHashSet<>();

        for (final Map.Entry<String, ?> entry : map.entrySet()) {
            final String name = entry.getKey();
            result.add(parseExplicitType(name, asMap(entry.getValue())));
        }

        return result;
    }

    private Set<Message> messagesFromYaml(final Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<Message> result = new LinkedHashSet<>();

        for (final Map.Entry<String, ?> entry : map.entrySet()) {
            final String name = entry.getKey();
            result.add(parseExplicitMessage(name, asMap(entry.getValue())));
        }

        return result;
    }

    private static <T> T required(final String key, final Optional<T> value) {
        return value.orElseThrow(() -> keyMissingError(key));
    }

    private static Set<String> asSet(final String key, final Map<String, ?> map) {
        return required(key, asOptionalSet(key, map));
    }

    @SuppressWarnings("unchecked")
    private static Optional<Set<String>> asOptionalSet(final String key, final Map<String, ?> map) {
        final Object value = map.get(key);

        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof Collection<?>) {
            return Optional.of(new HashSet<>((Collection<String>) value));
        }

        throw wrongTypeError(key, Collection.class, value);
    }

    private static class Reference implements Iterable<String> {

        private final List<String> tokens;

        public Reference(final List<String> tokens) {
            this.tokens = tokens;
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("Reference must not be empty");
            }
        }

        @Override
        public Iterator<String> iterator() {
            return this.tokens.iterator();
        }

        public static Reference parse(final String ref) {
            return new Reference(Arrays.asList(ref.split("/+")));
        }

        public String last() {
            return this.tokens.get(this.tokens.size() - 1);
        }

    }

    private TypeReference parseType(final String name, final Map<String, ?> map) {
        final Optional<String> ref = asOptionalString("$ref", map);

        if (ref.isPresent()) {

            final Reference to = Reference.parse(ref.get());

            // FIXME: validate full ref syntax

            final String refName = to.last();
            final Type type = this.types.get(refName);
            if (type != null) {
                return type;
            }

            return new TypeReference(refName);
        } else {
            return parseExplicitType(name, map);
        }
    }

    private Type parseExplicitType(final String name, final Map<String, ?> map) {
        final String type = asString("type", map);
        switch (type) {
        case "string": {
            if (map.containsKey("enum")) {
                return addType(addCommonTypeInfo(parseEnumType(name, map), map));
            }
            return addType(addCommonTypeInfo(parseStringType(name, map), map));
        }
        case "object":
            return addType(addCommonTypeInfo(parseObjectType(name, map), map));
        default:
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

    private Type addType(final Type type) {
        this.types.put(type.getName(), type);
        return type;
    }

    private Type parseEnumType(final String name, final Map<String, ?> map) {
        final EnumType type = new EnumType(name);

        type.setLiterals(asSet("enum", map));

        return type;
    }

    private StringType parseStringType(final String name, final Map<String, ?> map) {
        final StringType type = new StringType(name);

        type.setFormat(asOptionalString("format", map).orElse(null));

        return type;
    }

    private Type parseObjectType(final String name, final Map<String, ?> map) {
        final ObjectType type = new ObjectType(name);

        final Set<String> required = asOptionalSet("required", map).orElse(Collections.emptySet());

        final Map<String, ?> prop = asMap("properties", map);

        for (final Map.Entry<String, ?> entry : prop.entrySet()) {
            final Property p = new Property();

            final String propName = entry.getKey();
            final Map<String, ?> propValues = asMap(entry.getValue());

            p.setName(propName);
            p.setDescription(asOptionalString("description", propValues).orElse(null));
            p.setRequired(required.contains(propName));
            p.setType(parseType(entry.getKey(), propValues));

            type.getProperties().add(p);
        }

        return type;
    }

    private Type addCommonTypeInfo(final Type type, final Map<String, ?> map) {
        type.setTitle(asOptionalString("title", map).orElse(null));
        type.setDescription(asOptionalString("description", map).orElse(null));
        return type;
    }

    private Set<Topic> topicsFromYaml(final Map<String, ?> topics) {
        final Set<Topic> result = new HashSet<>();

        for (final Map.Entry<String, ?> entry : topics.entrySet()) {
            result.add(topicFromYaml(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    private Topic topicFromYaml(final String key, final Object value) {
        final Map<String, ?> map = asMap(value);

        final Topic result = new Topic();

        result.setName(key);
        result.setPublish(asOptionalMap("publish", map).map(v -> parseMessage("Publish. " + key, v)).orElse(null));
        result.setSubscribe(asOptionalMap("subscribe", map).map(v -> parseMessage("Subscribe." + key, v)).orElse(null));

        return result;
    }

    private MessageReference parseMessage(final String name, final Map<String, ?> map) {
        final Optional<String> ref = asOptionalString("$ref", map);

        if (ref.isPresent()) {

            final Reference to = Reference.parse(ref.get());

            final String refName = to.last();

            return new MessageReference(refName);
        } else {
            return parseExplicitMessage(name, map);
        }
    }

    private Message parseExplicitMessage(final String name, final Map<String, ?> map) {

        final Message message = new Message(name);

        message.setDescription(asOptionalString("description", map).orElse(null));
        message.setSummary(asOptionalString("summary", map).orElse(null));

        message.setPayload(parseType("payload", asMap("payload", map)));

        this.messages.put(name, message);
        return message;
    }

    private Information infoFromYaml(final Map<String, ?> map) {
        final Information result = new Information();

        result.setTitle(asOptionalString("title", map).orElse(null));
        result.setVersion(asString("version", map));

        return result;
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

    private static IllegalStateException wrongTypeError(final String key, final Class<?> expected,
            final Object result) {
        return new IllegalStateException(String.format("Key%s is expected to be of type %s (but is %s instead)",
                key != null ? " '" + key + "'" : "", expected.getSimpleName(), result.getClass().getSimpleName()));
    }

    private static IllegalStateException keyMissingError(final String key) {
        return new IllegalStateException(String.format("Key '%s' is missing in map", key));
    }
}
