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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class EnumType extends ParentableType {
    private Set<String> literals = new LinkedHashSet<>();

    public EnumType(final String namespace, final List<String> parents, final String name) {
        super(namespace, parents, name);
    }

    public Set<String> getLiterals() {
        return this.literals;
    }

    public void setLiterals(final Set<String> literals) {
        this.literals = literals;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
