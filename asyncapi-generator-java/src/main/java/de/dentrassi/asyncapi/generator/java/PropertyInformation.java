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

public class PropertyInformation {
    private final String typeName;
    private final String name;
    private final String summary;
    private final String description;

    public PropertyInformation(final String typeName, final String name, final String summary, final String description) {
        this.typeName = typeName;
        this.name = name;
        this.summary = summary;
        this.description = description;
    }

    public String getTypeName() {
        return this.typeName;
    }

    public String getName() {
        return this.name;
    }

    public String getSummary() {
        return this.summary;
    }

    public String getDescription() {
        return this.description;
    }

}
