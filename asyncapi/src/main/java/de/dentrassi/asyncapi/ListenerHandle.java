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

import java.util.concurrent.CompletionStage;

/**
 * A handle to a listener
 * <p>
 * This is returned by calls to {@link Subscribe} methods which create a
 * subscription in order to listen for incoming messages.
 * </p>
 * <p>
 * This listener extends both {@link AutoCloseable} as well as
 * {@link CompletionStage}. Subscriptions have to be closed when no more
 * messages should be consumed. This can be achieved by calling
 * {@link #close()}. Establishing a subscription is performed asynchronously and
 * the progress can be tracked using the {@link CompletionStage} methods.
 * </p>
 */
public interface ListenerHandle extends AutoCloseable, CompletionStage<Void> {

}
