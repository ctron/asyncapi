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

package de.dentrassi.asyncapi.type;

import java.util.Collections;
import java.util.List;

public class ParentableType extends Type {

    private final List<String> parents;

    public ParentableType(final String namespace, final List<String> parents, final String name) {
        super(namespace, name);
        this.parents = Collections.unmodifiableList(parents);
    }

    public List<String> getParents() {
        return this.parents;
    }

}
