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

package de.dentrassi.asyncapi.util;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import de.dentrassi.asyncapi.ListenerHandle;
import de.dentrassi.asyncapi.Publish;
import de.dentrassi.asyncapi.PublishSubscribe;
import de.dentrassi.asyncapi.Subscribe;

/**
 * A helper class to provide an aggregated publish-subscriber
 *
 * @param <P>
 *            The publish message class
 * @param <S>
 *            The subscribe message class
 */
public class AggregatePublishSubscriber<P, S> implements PublishSubscribe<P, S> {

    private final Publish<P> publish;
    private final Subscribe<S> subscribe;

    public AggregatePublishSubscriber(final Publish<P> publish, final Subscribe<S> subscribe) {
        Objects.requireNonNull(publish, "'publish' must not be null");
        Objects.requireNonNull(subscribe, "'subscribe' must not be null");

        this.publish = publish;
        this.subscribe = subscribe;
    }

    @Override
    public CompletionStage<?> publish(final P message) {
        return this.publish.publish(message);
    }

    @Override
    public ListenerHandle subscribe(final Consumer<S> consumer) {
        return this.subscribe.subscribe(consumer);
    }

}
