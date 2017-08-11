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

import static de.dentrassi.asyncapi.generator.java.Names.toCamelCase;
import static de.dentrassi.asyncapi.generator.java.Names.toLowerDash;
import static de.dentrassi.asyncapi.generator.java.Names.toUpperUnderscore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NamesTest {

    @Test
    public void testNull() {
        assertNull(toCamelCase(null, false));
        assertNull(toCamelCase(null, true));
    }

    @Test
    public void testSimple1() {
        assertEquals("", toCamelCase("", true));
    }

    @Test
    public void testSimple2() {
        assertEquals("Foo", toCamelCase("Foo", true));
    }

    @Test
    public void testSimple3() {
        assertEquals("Foo", toCamelCase("foo", true));
    }

    @Test
    public void testSimple4() {
        assertEquals("foo", toCamelCase("Foo", false));
    }

    @Test
    public void testSimple5() {
        assertEquals("foo", toCamelCase("foo", false));
    }

    @Test
    public void testMore1() {
        assertEquals("fooBarBaz", toCamelCase("FooBarBaz", false));
    }

    @Test
    public void testMore2() {
        assertEquals("FooBarBaz", toCamelCase("FOO_BAR_BAZ", true));
    }

    @Test
    public void testMore3() {
        assertEquals("FooBarBaz", toCamelCase("foo-bar-baz", true));
    }

    @Test
    public void testAll1() {
        assertAll("FOO_BAR_BAZ", "fooBarBaz", "FooBarBaz", "foo-bar-baz", "FOO_BAR_BAZ");
    }

    @Test
    public void testAll2() {
        assertAll("FooBarBaz", "fooBarBaz", "FooBarBaz", "foo-bar-baz", "FOO_BAR_BAZ");
    }

    @Test
    public void testAll3() {
        assertAll("foo-bar-baz", "fooBarBaz", "FooBarBaz", "foo-bar-baz", "FOO_BAR_BAZ");
    }

    public static void assertAll(final String text, final String camelLower, final String camelUpper, final String lowerDash, final String upperUnderscore) {
        assertEquals(camelLower, toCamelCase(text, false));
        assertEquals(camelUpper, toCamelCase(text, true));

        assertEquals(lowerDash, toLowerDash(text));
        assertEquals(upperUnderscore, toUpperUnderscore(text));
    }
}
