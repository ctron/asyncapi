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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.generator.java.Generator.Builder;
import de.dentrassi.asyncapi.generator.java.jms.JmsClientGenerator;
import de.dentrassi.asyncapi.internal.parser.YamlParser;

public class Application1 {
    public static void main(final String[] args) throws Exception {
        final Path path = Paths.get("asyncapi-example.yaml");

        try (final InputStream in = Files.newInputStream(path)) {

            final AsyncApi api = new YamlParser(in).parse();

            final Builder builder = Generator.newBuilder();

            builder.targetPath(Paths.get("target/generated"));
            builder.addExtension(new JmsClientGenerator());

            builder.build(api).generate();
        }

    }
}
