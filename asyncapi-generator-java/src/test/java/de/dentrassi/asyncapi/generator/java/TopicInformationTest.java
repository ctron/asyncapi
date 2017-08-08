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

import de.dentrassi.asyncapi.generator.java.TopicInformation.Status;

public class TopicInformationTest {

    @Test
    public void testOk1() {
        final TopicInformation ti = TopicInformation.fromString("service.1.event.foo.bar.do");
        assertTopicInformation(ti, "service", "1", "event", "foo.bar", "do", null);
    }

    @Test
    public void testOk2() {
        final TopicInformation ti = TopicInformation.fromString("service.1.0.event.foo.do");
        assertTopicInformation(ti, "service", "1.0", "event", "foo", "do", null);
    }

    @Test
    public void testOk3() {
        final TopicInformation ti = TopicInformation.fromString("service.1.0.event.foo.bar.do.done");
        assertTopicInformation(ti, "service", "1.0", "event", "foo.bar", "do", Status.DONE);
    }

    @Test
    public void testOk4() {
        final TopicInformation ti = TopicInformation.fromString("service.1.0.action.foo.bar.do.done");
        assertTopicInformation(ti, "service", "1.0", "action", "foo.bar.do", "done", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failShort0() {
        TopicInformation.fromString("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failShort1() {
        TopicInformation.fromString("a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failShort2() {
        TopicInformation.fromString("a.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failShort3() {
        TopicInformation.fromString("a.1.b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failShort4() {
        TopicInformation.fromString("a.1.b.c");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failVersion1() {
        TopicInformation.fromString("a.X.b.c.d");
    }

    private void assertTopicInformation(final TopicInformation ti, final String service, final String version, final String type, final String resources, final String action,
            final Status status) {

        Assert.assertEquals(service, ti.getService());
        Assert.assertEquals(version, ti.getVersion());
        Assert.assertEquals(type, ti.getType());
        Assert.assertEquals(resources, String.join(".", ti.getResources()));
        Assert.assertEquals(action, ti.getAction());
        Assert.assertEquals(status, ti.getStatus().orElse(null));
    }
}
