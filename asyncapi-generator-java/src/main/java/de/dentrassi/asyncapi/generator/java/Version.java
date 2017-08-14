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
import java.util.stream.Collectors;

public class Version implements Comparable<Version> {
    private final int[] version;

    private Version(final int[] version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return Arrays.stream(this.version).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }

    @Override
    public int compareTo(final Version other) {
        final int max = Math.max(this.version.length, other.version.length);

        for (int i = 0; i < max; i++) {
            final int v1 = i < this.version.length ? this.version[i] : 0;
            final int v2 = i < other.version.length ? other.version[i] : 0;

            final int rc = Integer.compare(v1, v2);
            if (rc != 0) {
                return rc;
            }
        }

        return 0;
    }

    public static Version valueOf(final String version) {
        if (version == null) {
            return null;
        }

        final String[] toks = version.split("\\.");
        final int[] v = new int[toks.length];

        for (int i = 0; i < toks.length; i++) {
            v[i] = Integer.parseInt(toks[i]);
        }

        return new Version(v);
    }
}
