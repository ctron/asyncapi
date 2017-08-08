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

package de.dentrassi.asyncapi.generator.java;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class TopicInformation {

    private static final Pattern VERSION_SEG = Pattern.compile("[0-9]+");

    public static enum Status {
        QUEUED, SUCCEED, FAILED, DONE;
    }

    private final String service;
    private final String version;
    private final String type;
    private final List<String> resources;
    private final String action;
    private final Optional<Status> status;

    public TopicInformation(final String service, final String version, final String type, final List<String> resources, final String action, final Optional<Status> status) {
        this.service = service;
        this.version = version;
        this.type = type;
        this.resources = resources;
        this.action = action;
        this.status = status;
    }

    public String getService() {
        return this.service;
    }

    public String getType() {
        return this.type;
    }

    public String getVersion() {
        return this.version;
    }

    public List<String> getResources() {
        return this.resources;
    }

    public String getAction() {
        return this.action;
    }

    public Optional<Status> getStatus() {
        return this.status;
    }

    public static TopicInformation fromString(final String topic) {
        Objects.requireNonNull(topic);

        // split

        final LinkedList<String> toks = new LinkedList<>(Arrays.asList(topic.split("\\.")));

        // assign service

        final String service = toks.pollFirst();

        // assign version

        final LinkedList<String> version = new LinkedList<>();
        while (isVersionSegment(toks.peekFirst())) {
            version.add(toks.pollFirst());
        }

        // assign optional status

        final Optional<Status> status;

        final String type = toks.pollFirst();
        if ("event".equals(type) && toks.size() > 2) {
            final String last = toks.peekLast();
            Status statusValue = null;
            try {
                statusValue = Status.valueOf(last.toUpperCase());
                toks.pollLast(); // consume
            } catch (final IllegalArgumentException e) {
            }
            status = Optional.ofNullable(statusValue);
        } else {
            status = Optional.empty();
        }

        // assign action

        final String action = toks.pollLast();

        // assign resources

        final LinkedList<String> resources = toks;

        // validate

        if (service == null || service.isEmpty()) {
            throw new IllegalArgumentException("Wrong topic syntax");
        }
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Wrong topic syntax");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Wrong topic syntax");
        }
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Wrong topic syntax");
        }

        // return result

        return new TopicInformation(service, String.join(".", version), type, resources, action, status);
    }

    private static boolean isVersionSegment(final String string) {
        if (string == null) {
            return false;
        }
        return VERSION_SEG.matcher(string).matches();
    }
}
