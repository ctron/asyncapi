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

import static de.dentrassi.asyncapi.generator.java.PackageTypeBuilder.asPropertyName;
import static de.dentrassi.asyncapi.generator.java.PackageTypeBuilder.asTypeName;
import static java.util.Arrays.asList;
import static java.util.Optional.empty;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TypeLiteral;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.CoreType;
import de.dentrassi.asyncapi.EnumType;
import de.dentrassi.asyncapi.Information;
import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.MessageReference;
import de.dentrassi.asyncapi.ObjectType;
import de.dentrassi.asyncapi.Property;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.Type;
import de.dentrassi.asyncapi.TypeReference;

public class Generator {
    private static final String PUBSUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.PublishSubscribe";

    private static final String SUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.Subscribe";

    private static final String PUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.Publish";

    private static final String TOPIC_ANN_TYPE_NAME = "de.dentrassi.asyncapi.Topic";

    private final AsyncApi api;
    private Path target;
    private Charset characterSet = StandardCharsets.UTF_8;
    private String basePackage;
    private boolean validateTopicSyntax = true;

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

    public Generator validateTopicSyntax(final boolean validateTopicSyntax) {
        this.validateTopicSyntax = validateTopicSyntax;
        return this;
    }

    public void generate() throws IOException {
        Files.createDirectories(this.target);

        generateRoot();
        generateMessages();
        generateTypes();
        generateTopics();
    }

    private void generateTopics() {

        // prepare

        final Map<Topic, TopicInformation> topics = new LinkedHashMap<>(this.api.getTopics().size());
        final Map<String, Map<String, List<Topic>>> versions = new HashMap<>();

        for (final Topic topic : this.api.getTopics()) {

            TopicInformation ti;
            try {
                ti = TopicInformation.fromString(topic.getName());
            } catch (final IllegalArgumentException e) {
                if (this.validateTopicSyntax) {
                    throw e;
                }
                // fall back to default
                ti = new TopicInformation("Topics", "1", "event", new LinkedList<>(asList(topic.getName().split("\\."))), "send", empty());
            }

            addTopic(versions, ti, topic);
            topics.put(topic, ti);
        }

        renderServices(topics, versions);
        renderClient(topics, versions);
    }

    private static class NewestService {
        private final TypeInformation type;
        private final Version version;

        public NewestService(final TypeInformation type, final Version version) {
            this.type = type;
            this.version = version;
        }

        public TypeInformation getType() {
            return this.type;
        }

        public Version getVersion() {
            return this.version;
        }
    }

    private void renderClient(final Map<Topic, TopicInformation> topics, final Map<String, Map<String, List<Topic>>> versions) {

        final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName(null), this.characterSet, type -> null);
        builder.createType(new TypeInformation("Client", null, null), true, false, b -> {

            final Map<String, NewestService> latest = new HashMap<>();

            for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : versions.entrySet()) {
                final String version = makeVersion(versionEntry.getKey());

                b.createType(new TypeInformation(version.toUpperCase(), null, null), true, false, vb -> {

                    for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {

                        final TypeInformation serviceType = createServiceType(serviceEntry);

                        vb.createMethod((ast, cu) -> {
                            final MethodDeclaration md = ast.newMethodDeclaration();
                            md.setName(ast.newSimpleName(PackageTypeBuilder.asPropertyName(serviceType.getName())));
                            md.setReturnType2(ast.newSimpleType(ast.newName(packageName(version + "." + serviceType.getName()))));
                            return md;
                        });

                        // record latest version

                        final Version v = Version.valueOf(versionEntry.getKey());
                        final NewestService lv = latest.get(serviceType.getName());
                        if (lv == null || v.compareTo(lv.getVersion()) > 0) {
                            latest.put(serviceType.getName(), new NewestService(serviceType, v));
                        }

                    }

                });

                // create version method - e.g. v1()

                b.createMethod((ast, cu) -> {
                    final MethodDeclaration md = ast.newMethodDeclaration();
                    md.setName(ast.newSimpleName(version));
                    md.setReturnType2(ast.newSimpleType(ast.newSimpleName(version.toUpperCase())));
                    return md;
                });

            }

            // create latest versions

            for (final Map.Entry<String, NewestService> latestEntry : latest.entrySet()) {
                b.createMethod((ast, cu) -> {
                    return createReturnLatestVersionService(latestEntry, ast);
                });
            }

        });

    }

    @SuppressWarnings("unchecked")
    private MethodDeclaration createReturnLatestVersionService(final Map.Entry<String, NewestService> latestEntry, final AST ast) {
        final String version = makeVersion(latestEntry.getValue().getVersion().toString());
        final String serviceType = packageName(version + "." + latestEntry.getValue().getType().getName());

        final MethodDeclaration md = ast.newMethodDeclaration();

        md.setName(ast.newSimpleName(asPropertyName(latestEntry.getKey())));
        md.setReturnType2(ast.newSimpleType(ast.newName(serviceType)));

        md.modifiers().add(ast.newModifier(ModifierKeyword.DEFAULT_KEYWORD));

        final Block block = ast.newBlock();
        md.setBody(block);

        final ReturnStatement ret = ast.newReturnStatement();
        block.statements().add(ret);

        final MethodInvocation versionMethod = ast.newMethodInvocation();
        versionMethod.setName(ast.newSimpleName(version.toLowerCase()));

        final MethodInvocation serviceMethod = ast.newMethodInvocation();
        serviceMethod.setName(ast.newSimpleName(asPropertyName(latestEntry.getKey())));
        serviceMethod.setExpression(versionMethod);

        ret.setExpression(serviceMethod);

        return md;
    }

    @SuppressWarnings("unchecked")
    private void renderServices(final Map<Topic, TopicInformation> topics, final Map<String, Map<String, List<Topic>>> versions) {
        for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : versions.entrySet()) {

            final String version = makeVersion(versionEntry.getKey());
            final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName(version), this.characterSet, type -> null);

            for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {
                builder.createType(createServiceType(serviceEntry), true, false, b -> {

                    for (final Topic topic : serviceEntry.getValue()) {
                        b.createMethod((ast, cu) -> {

                            final TopicInformation ti = topics.get(topic);

                            // new method

                            final MethodDeclaration md = ast.newMethodDeclaration();
                            md.setName(ast.newSimpleName(makeTopicMethodName(ti)));

                            // return type

                            // set return type

                            md.setReturnType2(evalEventMethodType(ast, topic));

                            // assign annotation

                            final NormalAnnotation an = ast.newNormalAnnotation();
                            an.setTypeName(ast.newName(TOPIC_ANN_TYPE_NAME));
                            an.values().add(newKeyValueString(ast, "name", topic.getName()));
                            if (topic.getPublish() != null) {
                                an.values().add(newKeyValueClass(ast, "publish", messageTypeName(topic.getPublish())));
                            }
                            if (topic.getSubscribe() != null) {
                                an.values().add(newKeyValueClass(ast, "subscribe", messageTypeName(topic.getSubscribe())));
                            }
                            md.modifiers().add(an);

                            // make public

                            PackageTypeBuilder.makePublic(md);

                            // return

                            return md;

                        });

                    }

                });
            }

        }
    }

    private TypeInformation createServiceType(final Map.Entry<String, List<Topic>> serviceEntry) {
        return new TypeInformation(asTypeName(serviceEntry.getKey()), null, null);
    }

    @SuppressWarnings("unchecked")
    private ParameterizedType evalEventMethodType(final AST ast, final Topic topic) {

        final MessageReference pubMsg = topic.getPublish();
        final MessageReference subMsg = topic.getSubscribe();

        if (pubMsg == null && subMsg == null) {
            return null;
        }

        final SimpleType eventType;

        if (pubMsg != null && subMsg != null) {
            eventType = ast.newSimpleType(ast.newName(PUBSUB_CLASS_TYPE_NAME));
        } else if (pubMsg != null) {
            eventType = ast.newSimpleType(ast.newName(PUB_CLASS_TYPE_NAME));
        } else {
            eventType = ast.newSimpleType(ast.newName(SUB_CLASS_TYPE_NAME));
        }

        final ParameterizedType type = ast.newParameterizedType(eventType);

        if (pubMsg != null) {
            type.typeArguments().add(ast.newSimpleType(ast.newName(messageTypeName(pubMsg))));
        }
        if (subMsg != null) {
            type.typeArguments().add(ast.newSimpleType(ast.newName(messageTypeName(subMsg))));
        }

        return type;
    }

    private static MemberValuePair newKeyValueClass(final AST ast, final String key, final String typeName) {

        final MemberValuePair pair = ast.newMemberValuePair();

        pair.setName(ast.newSimpleName(key));

        final TypeLiteral type = ast.newTypeLiteral();
        type.setType(ast.newSimpleType(ast.newName(typeName)));

        pair.setValue(type);

        return pair;
    }

    private static MemberValuePair newKeyValueString(final AST ast, final String key, final String value) {

        final MemberValuePair pair = ast.newMemberValuePair();

        pair.setName(ast.newSimpleName(key));

        final StringLiteral topicLiteral = ast.newStringLiteral();
        topicLiteral.setLiteralValue(value);

        pair.setValue(topicLiteral);

        return pair;
    }

    private String messageTypeName(final MessageReference message) {
        return packageName("messages") + "." + PackageTypeBuilder.asTypeName(message.getName());
    }

    private String makeTopicMethodName(final TopicInformation ti) {

        Stream<String> s = Stream.of(ti.getType());

        s = Stream.concat(s, ti.getResources().stream());
        s = Stream.concat(s, Stream.of(ti.getAction()));

        if (ti.getStatus().isPresent()) {
            s = Stream.concat(s, Stream.of(ti.getStatus().get().toString().toLowerCase()));
        }

        return joinLowerCamelCase(s);
    }

    private static String joinLowerCamelCase(final Stream<String> s) {

        boolean first = true;

        final StringBuilder sb = new StringBuilder();

        for (final String tok : (Iterable<String>) s::iterator) {
            final int len = tok.length();

            if (len == 0) {
                continue;
            }

            final Function<Character, Character> fn = first ? Character::toLowerCase : Character::toUpperCase;
            first = false;

            sb.append(fn.apply(tok.charAt(0)));
            sb.append(tok.substring(1).toLowerCase());
        }

        return sb.toString();
    }

    private static String makeVersion(final String version) {
        return "v" + version.replace(".", "_");
    }

    private static void addTopic(final Map<String, Map<String, List<Topic>>> versions, final TopicInformation ti, final Topic topic) {

        Map<String, List<Topic>> version = versions.get(ti.getVersion());
        if (version == null) {
            version = new HashMap<>();
            versions.put(ti.getVersion(), version);
        }

        List<Topic> service = version.get(ti.getService());
        if (service == null) {
            service = new LinkedList<>();
            version.put(ti.getService(), service);
        }

        service.add(topic);
    }

    private void generateMessages() {
        final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName("messages"), this.characterSet, this::lookupType);

        this.api.getMessages().forEach(message -> {
            generateMessage(builder, message);
        });
    }

    private Type lookupType(final String typeName) {
        return this.api.getTypes().stream().filter(type -> type.getName().equals(typeName)).findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("Unknown type '%s' referenced", typeName)));
    }

    private void generateMessage(final TypeBuilder builder, final Message message) {

        final TypeInformation ti = new TypeInformation(asTypeName(message.getName()), message.getSummary(), message.getDescription());

        final String payloadTypeName = PackageTypeBuilder.asTypeName(message.getPayload().getName());

        builder.createType(ti, false, false, b -> {

            if (message.getPayload() instanceof ObjectType) {

                generateType(b, (Type) message.getPayload());

                b.createProperty(new PropertyInformation("Payload", "payload", "Message payload", null));

            } else if (message.getPayload() instanceof CoreType) {

                final String typeName = ((CoreType) message.getPayload()).getJavaType().getName();
                b.createProperty(new PropertyInformation(typeName, "payload", "Message payload", null));

            } else if (message.getPayload().getClass().equals(TypeReference.class)) {
                b.createProperty(new PropertyInformation(packageName("types." + payloadTypeName), "payload", "Message payload", null));
            } else {
                throw new IllegalStateException("Unsupported payload type: " + message.getPayload().getClass().getName());
            }

        });
    }

    private void generateTypes() {

        final TypeBuilder builder = new PackageTypeBuilder(this.target, packageName("types"), this.characterSet, typeName -> {
            return this.api.getTypes().stream().filter(type -> type.getName().equals(typeName)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(String.format("Unknown type '%s' referenced", typeName)));
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

        final TypeInformation ti = new TypeInformation(asTypeName(type.getName()), type.getTitle(), type.getDescription());

        builder.createType(ti, false, true, b -> {

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

            if (property.getType() instanceof CoreType) {
                typeName = ((CoreType) type).getJavaType().getName();
            } else {
                typeName = PackageTypeBuilder.asTypeName(type.getName());
            }

        } else {
            type = lookupType(property.getType().getName());
            typeName = packageName("types." + PackageTypeBuilder.asTypeName(type.getName()));
        }

        // need to check resolved type

        if (type instanceof CoreType) {
            typeName = ((CoreType) type).getJavaType().getName();
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
        builder.createEnum(new TypeInformation(asTypeName(type.getName()), type.getTitle(), type.getDescription()), type.getLiterals());
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

        PackageTypeBuilder.createCompilationUnit(this.target, packageName(null), "package-info", this.characterSet, (ast, cu) -> {
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
