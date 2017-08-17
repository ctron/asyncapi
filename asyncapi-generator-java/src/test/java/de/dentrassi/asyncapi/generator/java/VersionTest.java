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

import org.junit.Assert;
import org.junit.Test;

import de.dentrassi.asyncapi.generator.java.util.Version;

public class VersionTest {

    @Test
    public void testCompare1() {
        assertVersionCompare(0, "1", "1.0");
    }

    @Test
    public void testCompare2() {
        assertVersionCompare(1, "2", "1.0");
    }

    @Test
    public void testCompare3() {
        assertVersionCompare(1, "2.0", "1.0");
    }

    @Test
    public void testCompare4() {
        assertVersionCompare(-1, "2.0", "4.0.0");
    }

    @Test
    public void testCompare5() {
        assertVersionCompare(1, "1.2.3", "1.1.5");
    }

    private static void assertVersionCompare(final int rc, final String v1, final String v2) {
        Assert.assertEquals(rc, Version.valueOf(v1).compareTo(Version.valueOf(v2)));
    }
}
