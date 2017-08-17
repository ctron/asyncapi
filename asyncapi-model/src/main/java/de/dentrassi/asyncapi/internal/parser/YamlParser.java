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

import static de.dentrassi.asyncapi.AsyncApi.VERSION;
import static de.dentrassi.asyncapi.internal.parser.Consume.asMap;
import static de.dentrassi.asyncapi.internal.parser.Consume.asOptionalMap;
import static de.dentrassi.asyncapi.internal.parser.Consume.asOptionalSet;
import static de.dentrassi.asyncapi.internal.parser.Consume.asOptionalString;
import static de.dentrassi.asyncapi.internal.parser.Consume.asSet;
import static de.dentrassi.asyncapi.internal.parser.Consume.asString;

import java.io.InputStream;
import java.io.Reader;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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

import de.dentrassi.asyncapi.ArrayType;
import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.CoreType;
import de.dentrassi.asyncapi.EnumType;
import de.dentrassi.asyncapi.Information;
import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.MessageReference;
import de.dentrassi.asyncapi.ObjectType;
import de.dentrassi.asyncapi.Property;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.Type;
import de.dentrassi.asyncapi.TypeReference;

/**
 * Parser for AsyncAPI definitions encoded as YAML
 */
public class YamlParser {

    private final Map<String, ?> document;

    private final Map<String, Message> messages = new HashMap<>();

    public YamlParser(final InputStream in) throws ParserException {
        try {
            this.document = asMap(new Yaml().load(in));
        } catch (final Exception e) {
            throw new ParserException("Failed to parse YAML document", e);
        }
    }

    public YamlParser(final Reader reader) throws ParserException {
        try {
            this.document = asMap(new Yaml().load(reader));
        } catch (final Exception e) {
            throw new ParserException("Failed to parse YAML document", e);
        }
    }

    public AsyncApi parse() {
        final String version = asString("asyncapi", this.document);

        if (!VERSION.equals(version)) {
            throw new IllegalStateException(String.format("Only version '%s' is supported, this is version '%s'", VERSION, version));
        }

        final AsyncApi api = new AsyncApi();

        api.setBaseTopic(asOptionalString("baseTopic", this.document).orElse(null));
        api.setHost(asString("host", this.document));
        api.setSchemes(asSet("schemes", this.document));
        api.setInformation(parseInfo(asMap("info", this.document)));
        api.setTopics(parseTopics(asMap("topics", this.document)));

        final Map<String, ?> components = asMap("components", this.document);

        api.setMessages(parseMessages(asOptionalMap("messages", components).orElse(null)));
        api.setTypes(parseTypes(asOptionalMap("schemas", components).orElse(null)));

        return api;
    }

    private Set<Type> parseTypes(final Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<Type> result = new LinkedHashSet<>();

        for (final Map.Entry<String, ?> entry : map.entrySet()) {
            final String name = entry.getKey();
            result.add(parseExplicitType("types", Collections.emptyList(), name, asMap(entry.getValue())));
        }

        return result;
    }

    private Set<Message> parseMessages(final Map<String, ?> map) {
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
            return last(0);
        }

        public String last(final int reverseIndex) {
            return this.tokens.get(this.tokens.size() - (reverseIndex + 1));
        }

    }

    private TypeReference parseType(final String namespace, final List<String> parents, final String name, final Map<String, ?> map) {
        final Optional<String> ref = asOptionalString("$ref", map);

        if (ref.isPresent()) {

            final Reference to = Reference.parse(ref.get());

            // FIXME: validate full ref syntax

            return new TypeReference(mapPackageName(to.last(1)), to.last());
        } else {
            return parseExplicitType(namespace, parents, name, map);
        }
    }

    private String mapPackageName(final String type) {
        if ("schemas".equals(type)) {
            return "types";
        }
        return type;
    }

    private Type parseExplicitType(final String namespace, final List<String> parents, final String name, final Map<String, ?> map) {

        final String type = asString("type", map);
        switch (type) {
        case "boolean":
            return addCommonTypeInfo(new CoreType(name, Boolean.class), map);
        case "integer":
            return addCommonTypeInfo(new CoreType(name, Integer.class), map);
        case "number":
            return addCommonTypeInfo(new CoreType(name, Double.class), map);
        case "string": {
            if (map.containsKey("enum")) {
                return addCommonTypeInfo(parseEnumType(namespace, parents, name, map), map);
            }
            return addCommonTypeInfo(parseCoreType(name, map), map);
        }
        case "array":
            return addCommonTypeInfo(parseArrayType(namespace, parents, name, map), map);
        case "object":
            return addCommonTypeInfo(parseObjectType(namespace, parents, name, map), map);
        default:
            throw new IllegalStateException(String.format("Unsupported type: %s", type));
        }
    }

    private static List<String> push(final List<String> parents, final String name) {
        final List<String> result = new ArrayList<>(parents);
        result.add(name);
        return result;
    }

    private Type parseArrayType(final String namespace, final List<String> parents, final String name, final Map<String, ?> map) {

        final boolean uniqueItems = Consume.asBoolean(map, "uniqueItems");

        final TypeReference itemType = parseType(namespace, parents, name + "Item", asMap("items", map));

        final ArrayType type = new ArrayType(name, itemType, uniqueItems);

        return type;
    }

    private Type parseEnumType(final String namespace, final List<String> parents, final String name, final Map<String, ?> map) {
        final EnumType type = new EnumType(namespace, parents, name);

        type.setLiterals(asSet("enum", map));

        return type;
    }

    private CoreType parseCoreType(final String name, final Map<String, ?> map) {

        final String format = asOptionalString("format", map).orElse(null);

        if (format == null) {
            return new CoreType(name, String.class);
        }

        switch (format) {
        case "date-time":
            return new CoreType(name, ZonedDateTime.class);
        default:
            throw new IllegalStateException(String.format("Unknown data format: " + format));
        }
    }

    private Type parseObjectType(final String namespace, final List<String> parents, final String name, final Map<String, ?> map) {
        final ObjectType type = new ObjectType(namespace, parents, name);

        final Set<String> required = asOptionalSet("required", map).orElse(Collections.emptySet());

        final Map<String, ?> prop = asMap("properties", map);

        for (final Map.Entry<String, ?> entry : prop.entrySet()) {
            final Property p = new Property();

            final String propName = entry.getKey();
            final Map<String, ?> propValues = asMap(entry.getValue());

            p.setName(propName);
            p.setDescription(asOptionalString("description", propValues).orElse(null));
            p.setRequired(required.contains(propName));
            p.setType(parseType(namespace, push(parents, name), entry.getKey(), propValues));

            type.getProperties().add(p);
        }

        return type;
    }

    private Type addCommonTypeInfo(final Type type, final Map<String, ?> map) {
        type.setTitle(asOptionalString("title", map).orElse(null));
        type.setDescription(asOptionalString("description", map).orElse(null));
        return type;
    }

    private Set<Topic> parseTopics(final Map<String, ?> topics) {
        final Set<Topic> result = new HashSet<>();

        for (final Map.Entry<String, ?> entry : topics.entrySet()) {
            result.add(parseTopic(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    private Topic parseTopic(final String key, final Object value) {
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

        message.setPayload(parseType("messages", Collections.singletonList(name), "payload", asMap("payload", map)));

        this.messages.put(name, message);
        return message;
    }

    private Information parseInfo(final Map<String, ?> map) {
        final Information result = new Information();

        result.setTitle(asOptionalString("title", map).orElse(null));
        result.setVersion(asString("version", map));

        return result;
    }

}
