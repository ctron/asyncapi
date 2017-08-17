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

package de.dentrassi.asyncapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import de.dentrassi.asyncapi.internal.parser.ParserException;
import de.dentrassi.asyncapi.internal.parser.YamlParser;

public class AsyncApi {

    public static final String VERSION = "1.0.0";

    private Information information;

    private String baseTopic;

    private Set<String> schemes = new HashSet<>();

    private String host;

    private Set<Topic> topics = new LinkedHashSet<>();

    private Set<Message> messages = new LinkedHashSet<>();

    private Set<Type> types = new LinkedHashSet<>();

    public Set<Topic> getTopics() {
        return this.topics;
    }

    public void setTopics(final Set<Topic> topics) {
        this.topics = topics;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public String getBaseTopic() {
        return this.baseTopic;
    }

    public void setBaseTopic(final String baseTopic) {
        this.baseTopic = baseTopic;
    }

    public Set<String> getSchemes() {
        return this.schemes;
    }

    public void setSchemes(final Set<String> schemes) {
        this.schemes = schemes;
    }

    public Information getInformation() {
        return this.information;
    }

    public void setInformation(final Information info) {
        this.information = info;
    }

    public Set<Message> getMessages() {
        return this.messages;
    }

    public void setMessages(final Set<Message> messages) {
        this.messages = messages;
    }

    public Set<Type> getTypes() {
        return this.types;
    }

    public void setTypes(final Set<Type> types) {
        this.types = types;
    }

    /**
     * Load an AsyncAPI specification encoded in YAML
     *
     * @param in
     *            the stream to read from
     * @return the model
     * @throws ParserException
     *             in case the document cannot be parsed
     */
    public static AsyncApi parseYaml(final InputStream in) throws ParserException {
        return validate(new YamlParser(in).parse());
    }

    /**
     * Load an AsyncAPI specification encoded in YAML
     *
     * @param reader
     *            the reader to read from
     * @return the model
     * @throws ParserException
     *             in case the document cannot be parsed
     */
    public static AsyncApi parseYaml(final Reader reader) throws ParserException {
        return validate(new YamlParser(reader).parse());
    }

    /**
     * Load an AsyncAPI specification encoded in YAML
     *
     * @param path
     *            the file system resource to read from
     * @return the model
     * @throws ParserException
     *             in case the document cannot be parsed
     */
    public static AsyncApi parseYaml(final Path path) throws ParserException {
        try (final InputStream in = Files.newInputStream(path)) {
            return validate(new YamlParser(in).parse());
        } catch (final IOException e) {
            throw new ParserException("Failed to read file", e);
        }
    }

    public static AsyncApi validate(final AsyncApi api) {

        final Validator validator = new Validator();
        validator.validate(api);

        if (validator.hasErrors()) {
            throw new ValidationException(validator.getMarkers());
        }

        return api;
    }
}
