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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.EnumType;
import de.dentrassi.asyncapi.Information;
import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.ObjectType;
import de.dentrassi.asyncapi.Property;
import de.dentrassi.asyncapi.StringType;
import de.dentrassi.asyncapi.Type;
import de.dentrassi.asyncapi.TypeReference;

public class Generator {
    private final AsyncApi api;
    private Path target;
    private Charset characterSet = StandardCharsets.UTF_8;
    private String basePackage;

    public Generator(final AsyncApi api) {
        this.api = api;
    }

    public Generator basePackage(final String basePackage) {
        this.basePackage = basePackage;
        return this;
    }

    public Generator target(final Path target) {
        this.target = target;
        return this;
    }

    public Generator characterSet(final Charset characterSet) {
        this.characterSet = characterSet;
        return this;
    }

    public void generate() throws IOException {
        Files.createDirectories(this.target);

        generateRoot();
        generateMessages();
        generateTypes();
    }

    private void generateMessages() {
        final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName("messages"), StandardCharsets.UTF_8,
                this::lookupType);

        this.api.getMessages().forEach(message -> {
            generateMessage(builder, message);
        });
    }

    private Type lookupType(final String typeName) {
        return this.api.getTypes().stream().filter(type -> type.getName().equals(typeName)).findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("Unknown type '%s' referenced", typeName)));
    }

    private void generateMessage(final TypeBuilder builder, final Message message) {

        final TypeInformation ti = new TypeInformation(PackageTypeBuilder.asTypeName(message.getName()),
                message.getSummary(), message.getDescription());

        final String payloadTypeName = PackageTypeBuilder.asTypeName(message.getPayload().getName());

        builder.createClass(ti, b -> {

            if (message.getPayload() instanceof ObjectType) {

                generateType(b, (Type) message.getPayload());

                b.createProperty(new PropertyInformation("Payload", "payload", "Message payload", null));

            } else if (message.getPayload() instanceof StringType) {
                b.createProperty(new PropertyInformation("String", "payload", "Message payload", null));

            } else if (message.getPayload().getClass().equals(TypeReference.class)) {
                b.createProperty(new PropertyInformation(packageName("types." + payloadTypeName), "payload",
                        "Message payload", null));
            } else {
                throw new IllegalStateException(
                        "Unsupported payload type: " + message.getPayload().getClass().getName());
            }

        });
    }

    private void generateTypes() {

        final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName("types"), StandardCharsets.UTF_8,
                typeName -> {
                    return this.api.getTypes().stream().filter(type -> type.getName().equals(typeName)).findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    String.format("Unknown type '%s' referenced", typeName)));
                });

        this.api.getTypes().forEach(type -> {
            generateType(builder, type);
        });
    }

    private void generateType(final TypeBuilder builder, final Type type) {
        if (type instanceof EnumType) {
            generateEnum((EnumType) type, builder);
        } else if (type instanceof ObjectType) {
            generateObject((ObjectType) type, builder);
        }
    }

    private void generateObject(final ObjectType type, final TypeBuilder builder) {

        final TypeInformation ti = new TypeInformation(PackageTypeBuilder.asTypeName(type.getName()), type.getTitle(),
                type.getDescription());

        builder.createClass(ti, b -> {

            for (final Property property : type.getProperties()) {
                generateProperty(property, b);
            }
        });

    }

    private void generateProperty(final Property property, final TypeBuilder builder) {
        final Type type;

        String typeName;

        if (property.getType() instanceof Type) {
            type = (Type) property.getType();
            generateType(builder, (Type) property.getType());

            if (property.getType() instanceof StringType) {
                typeName = "String";
            } else {
                typeName = PackageTypeBuilder.asTypeName(type.getName());
            }

        } else {
            type = lookupType(property.getType().getName());
            typeName = packageName("types." + PackageTypeBuilder.asTypeName(type.getName()));
        }

        // need to check resolved type
        if (type instanceof StringType) {
            typeName = "String";
        }

        final String name = PackageTypeBuilder.asPropertyName(property.getName());

        String summary = property.getDescription();
        String description = null;
        if (summary == null) {
            summary = type.getTitle();
            description = type.getDescription();
        }

        final PropertyInformation p = new PropertyInformation(typeName, name, summary, description);

        builder.createProperty(p);
    }

    private void generateEnum(final EnumType type, final TypeBuilder builder) {
        builder.createEnum(new TypeInformation(PackageTypeBuilder.asTypeName(type.getName()), type.getTitle(),
                type.getDescription()), type.getLiterals());
    }

    private String packageName(final String local) {
        String base = this.basePackage;
        if (base == null || base.isEmpty()) {
            base = this.api.getBaseTopic();
        }
        if (local == null || local.isEmpty()) {
            return base;
        } else {
            return base + "." + local;
        }
    }

    @SuppressWarnings("unchecked")
    private void generateRoot() throws IOException {

        PackageTypeBuilder.createCompilationUnit(this.target, packageName(null), "package-info", this.characterSet,
                (ast, cu) -> {
                    final Information info = this.api.getInformation();

                    final Javadoc doc = ast.newJavadoc();

                    if (info.getTitle() != null) {
                        final TagElement tag = ast.newTagElement();
                        tag.fragments().add(PackageTypeBuilder.newText(ast, info.getTitle()));
                        doc.tags().add(tag);
                    }

                    if (info.getVersion() != null) {
                        final TagElement version = ast.newTagElement();
                        version.setTagName("@version");
                        version.fragments().add(PackageTypeBuilder.newText(ast, info.getVersion()));
                        doc.tags().add(version);
                    }

                    cu.getPackage().setJavadoc(doc);

                });
    }
}
