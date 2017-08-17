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

package de.dentrassi.asyncapi.gson;

import static de.dentrassi.asyncapi.gson.time.DateTimeStrategy.ISO_8601_UTC;
import static de.dentrassi.asyncapi.gson.time.DateTimeStrategy.ISO_8601_ZONED;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Assert;
import org.junit.Test;

import de.dentrassi.asyncapi.Message;

public class TimeTests {

    public static class TestPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private Instant instant;

        private ZonedDateTime zonedDateTime;

        public Instant getInstant() {
            return this.instant;
        }

        public void setInstant(final Instant instant) {
            this.instant = instant;
        }

        public ZonedDateTime getZonedDateTime() {
            return this.zonedDateTime;
        }

        public void setZonedDateTime(final ZonedDateTime zonedDateTime) {
            this.zonedDateTime = zonedDateTime;
        }

    }

    public static class TestMessage implements Message<TestPayload> {

        private TestPayload payload;

        @Override
        public void setPayload(final TestPayload payload) {
            this.payload = payload;
        }

        @Override
        public TestPayload getPayload() {
            return this.payload;
        }

    }

    @Test
    public void testIso() throws Exception {

        // setup

        final Instant instant = Instant.now();
        final ZonedDateTime zonedDateTime = instant.atZone(ZoneId.of("CET"));

        final TestMessage message = createMessage(instant, zonedDateTime);

        final GsonPayloadFormat format = new GsonPayloadFormat(ISO_8601_UTC);

        // encode

        final String json = format.encode(message);

        // decode

        final TestMessage decode = format.decode(TestMessage.class, TestPayload.class, json);

        // assert

        /*
         * The instant should remain unchanged
         */

        Assert.assertEquals(instant, decode.getPayload().getInstant());

        /*
         * The zoned date time will loose its timezone and fall back to UTC
         */

        Assert.assertEquals(zonedDateTime.withZoneSameInstant(ZoneOffset.UTC), decode.getPayload().getZonedDateTime());
    }

    @Test
    public void testZoned() throws Exception {

        // setup

        final Instant instant = Instant.now();
        final ZonedDateTime zonedDateTime = instant.atZone(ZoneId.of("CET"));

        final TestMessage message = createMessage(instant, zonedDateTime);

        final GsonPayloadFormat format = new GsonPayloadFormat(ISO_8601_ZONED);

        // encode

        final String json = format.encode(message);

        // decode

        final TestMessage decode = format.decode(TestMessage.class, TestPayload.class, json);

        // assert

        /*
         * The instant should remain unchanged
         */

        Assert.assertEquals(instant, decode.getPayload().getInstant());

        /*
         * The zoned date time will loose its timezone and fall back to UTC
         */

        Assert.assertEquals(zonedDateTime, decode.getPayload().getZonedDateTime());
    }

    private TestMessage createMessage(final Instant instant, final ZonedDateTime zonedDateTime) {
        final TestPayload payload = new TestPayload();
        payload.setInstant(instant);
        payload.setZonedDateTime(zonedDateTime);

        final TestMessage message = new TestMessage();
        message.setPayload(payload);
        return message;
    }
}
