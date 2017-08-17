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

import org.apache.commons.lang3.builder.ToStringBuilder;

public class TypeReference {
    private final String namespace;
    private final String name;

    public TypeReference(final String namespace, final String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.name == null ? 0 : this.name.hashCode());
        result = prime * result + (this.namespace == null ? 0 : this.namespace.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof TypeReference)) {
            return false;
        }
        final TypeReference other = (TypeReference) obj;
        if (this.name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!this.name.equals(other.name)) {
            return false;
        }
        if (this.namespace == null) {
            if (other.namespace != null) {
                return false;
            }
        } else if (!this.namespace.equals(other.namespace)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
