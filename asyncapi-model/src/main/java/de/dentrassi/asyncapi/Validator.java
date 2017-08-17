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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Validator {

    public static class Marker {
        private final String message;
        private final List<Object> arguments;

        public Marker(final String message, final Object[] arguments) {
            this.message = message;
            this.arguments = Collections.unmodifiableList(Arrays.asList(arguments));
        }

        public String getMessage() {
            return this.message;
        }

        public List<Object> getArguments() {
            return this.arguments;
        }

        public String format() {
            return MessageFormat.format(this.message, this.arguments);
        }
    }

    private final List<Marker> markers = new LinkedList<>();

    public void validate(final AsyncApi api) {

        final String baseTopic = api.getBaseTopic();
        if (baseTopic != null && !baseTopic.isEmpty()) {
            if (!baseTopic.matches("^[^/.]")) {
                reportError("Base topic must match pattern: {0} - but is: {1}", "^[^/.]", baseTopic);
            }
        }

    }

    protected void reportError(final String message, final Object... arguments) {
        this.markers.add(new Marker(message, arguments));
    }

    public boolean hasErrors() {
        return !this.markers.isEmpty();
    }

    public List<Marker> getMarkers() {
        return this.markers;
    }
}
