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
import static de.dentrassi.asyncapi.generator.java.util.JDTHelper.makeProtected;
import static de.dentrassi.asyncapi.generator.java.util.JDTHelper.makePublic;
import static de.dentrassi.asyncapi.generator.java.util.JDTHelper.newStringLiteral;
import static de.dentrassi.asyncapi.generator.java.util.Names.makeVersion;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;

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
import de.dentrassi.asyncapi.generator.java.ServiceDefinitions.VersionedService;
import de.dentrassi.asyncapi.generator.java.util.JDTHelper;

public class Generator {

    public static final class Options {
        private Path targetPath;
        private Charset characterSet = StandardCharsets.UTF_8;
        private String basePackage;

        private Options() {
        }

        private Options(final Options other) {
            this.targetPath = other.targetPath;
            this.characterSet = other.characterSet;
            this.basePackage = other.basePackage;
        }

        public String getBasePackage() {
            return this.basePackage;
        }

        public Charset getCharacterSet() {
            return this.characterSet;
        }

        public Path getTargetPath() {
            return this.targetPath;
        }

        private void validate(final List<Exception> errors) {
            if (this.targetPath == null) {
                errors.add(new IllegalStateException("'targetPath' is not set"));
            }
        }
    }

    public static final class Builder {

        private final Options options = new Options();

        private boolean validateTopicSyntax = true;

        private final Set<GeneratorExtension> extensions = new HashSet<>();

        private Builder() {
        }

        public void addExtension(final GeneratorExtension extension) {
            Objects.requireNonNull(extension);

            this.extensions.add(extension);
        }

        public Builder validateTopicSyntax(final boolean validateTopicSyntax) {
            this.validateTopicSyntax = validateTopicSyntax;
            return this;
        }

        public Builder targetPath(final Path targetPath) {
            Objects.requireNonNull(targetPath);

            this.options.targetPath = targetPath;
            return this;
        }

        public Builder basePackage(final String basePackage) {
            this.options.basePackage = basePackage;
            return this;
        }

        public Builder characterSet(final Charset characterSet) {
            this.options.characterSet = characterSet;
            return this;
        }

        public Generator build(final AsyncApi api) {

            final LinkedList<Exception> errors = new LinkedList<>();
            this.options.validate(errors);

            if (!errors.isEmpty()) {
                final RuntimeException e = new RuntimeException("Invalid generator settings", errors.pollFirst());
                errors.stream().forEach(e::addSuppressed);
                throw e;
            }

            return new Generator(api, new Options(this.options), this.validateTopicSyntax, new ArrayList<>(this.extensions));
        }
    }

    public interface Context {
        public TypeBuilder createTypeBuilder(final String localPackageName);

        public String fullQualifiedName(String localName);

        public ServiceDefinitions getServiceDefinitions();
    }

    private static final String MESSAGE_IFACE_TYPE_NAME = "de.dentrassi.asyncapi.Message";

    private static final String PUBSUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.PublishSubscribe";

    private static final String SUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.Subscribe";

    private static final String PUB_CLASS_TYPE_NAME = "de.dentrassi.asyncapi.Publish";

    private static final String TOPIC_ANN_TYPE_NAME = "de.dentrassi.asyncapi.Topic";

    public static Builder newBuilder() {
        return new Builder();
    }

    private final AsyncApi api;
    private final boolean validateTopicSyntax;
    private List<GeneratorExtension> extensions = new ArrayList<>();

    private final Options options;

    private final Context context = new Context() {
        @Override
        public TypeBuilder createTypeBuilder(final String localPackageName) {
            return Generator.this.createTypeBuilder(localPackageName);
        }

        @Override
        public String fullQualifiedName(final String localName) {
            return packageName(localName);
        }

        @Override
        public ServiceDefinitions getServiceDefinitions() {
            return Generator.this.serviceDefinitions;
        }
    };

    private final ServiceDefinitions serviceDefinitions;

    private Generator(final AsyncApi api, final Options options, final boolean validateTopicSyntax, final List<GeneratorExtension> extensions) {
        this.api = api;
        this.options = options;
        this.validateTopicSyntax = validateTopicSyntax;
        this.extensions = extensions;

        this.serviceDefinitions = ServiceDefinitions.build(this.api, this.validateTopicSyntax);
    }

    public void generate() throws IOException {
        Files.createDirectories(this.options.getTargetPath());

        generateRoot();
        generateMessages();
        generateTypes();
        generateTopics();

        for (final GeneratorExtension extension : this.extensions) {
            extension.generate(this.api, this.options, this.context);
        }
    }

    private void generateTopics() {
        renderServices();
        renderClient();
    }

    private TypeBuilder createTypeBuilder(final String localPackageName) {
        return new PackageTypeBuilder(this.options.getTargetPath(), packageName(localPackageName), this.options.getCharacterSet(), type -> null);
    }

    private void renderClient() {

        final TypeBuilder builder = createTypeBuilder(null);

        final Consumer<TypeDeclaration> typeCustomizer = TypeBuilder.asInterface(true) //
                .andThen(TypeBuilder.superInterfaces(Arrays.asList("de.dentrassi.asyncapi.client.Client")));

        builder.createType(new TypeInformation("Client", null, null), typeCustomizer, b -> {

            renderDefaultClientBuilder(b);

            for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : this.serviceDefinitions.getVersions().entrySet()) {
                final String version = makeVersion(versionEntry.getKey());

                b.createType(new TypeInformation(version.toUpperCase(), null, null), true, false, vb -> {

                    for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {

                        final TypeInformation serviceType = createServiceTypeInformation(serviceEntry);

                        vb.createMethod((ast, cu) -> {
                            final MethodDeclaration md = ast.newMethodDeclaration();
                            md.setName(ast.newSimpleName(PackageTypeBuilder.asPropertyName(serviceType.getName())));
                            md.setReturnType2(ast.newSimpleType(ast.newName(packageName(version + "." + serviceType.getName()))));
                            return md;
                        });

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

            for (final Map.Entry<String, VersionedService> latestEntry : this.serviceDefinitions.getLatest().entrySet()) {
                b.createMethod((ast, cu) -> {
                    return createReturnLatestVersionService(latestEntry, ast);
                });
            }

        });

    }

    @SuppressWarnings("unchecked")
    private static ParameterizedType parametrizeSimple(final org.eclipse.jdt.core.dom.Type original, final String... parameters) {
        final AST ast = original.getAST();

        final ParameterizedType result = ast.newParameterizedType(original);

        for (final String name : parameters) {
            final SimpleType type = ast.newSimpleType(ast.newSimpleName(name));
            result.typeArguments().add(type);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void renderDefaultClientBuilder(final TypeBuilder builder) {

        Consumer<TypeDeclaration> typeCustomizer = td -> {
            final AST ast = td.getAST();

            final SimpleType st = ast.newSimpleType(ast.newName("de.dentrassi.asyncapi.client.Client.Builder"));

            td.setSuperclassType(parametrizeSimple(st, "B", "C"));
        };

        typeCustomizer = typeCustomizer.andThen(td -> {
            final AST ast = td.getAST();

            final TypeParameter b = ast.newTypeParameter();
            b.setName(ast.newSimpleName("B"));
            b.typeBounds().add(parametrizeSimple(ast.newSimpleType(ast.newSimpleName("Builder")), "B", "C"));

            final TypeParameter c = ast.newTypeParameter();
            c.setName(ast.newSimpleName("C"));
            c.typeBounds().add(ast.newSimpleType(ast.newSimpleName("Client")));

            td.typeParameters().add(b);
            td.typeParameters().add(c);
        });

        typeCustomizer = typeCustomizer.andThen(td -> JDTHelper.make(td, ModifierKeyword.STATIC_KEYWORD, ModifierKeyword.ABSTRACT_KEYWORD));

        builder.createType(new TypeInformation("Builder", null, null), typeCustomizer, b -> {

            b.createMethod((ast, cu) -> {
                final MethodDeclaration md = ast.newMethodDeclaration();
                md.setConstructor(true);
                md.setName(ast.newSimpleName("Builder"));
                makeProtected(md);

                final Block body = ast.newBlock();
                md.setBody(body);

                if (this.api.getHost() != null && !this.api.getHost().isEmpty()) {
                    final MethodInvocation mi = ast.newMethodInvocation();
                    mi.setName(ast.newSimpleName("host"));
                    mi.arguments().add(newStringLiteral(ast, this.api.getHost()));
                    body.statements().add(ast.newExpressionStatement(mi));
                }

                if (this.api.getBaseTopic() != null && !this.api.getBaseTopic().isEmpty()) {
                    final MethodInvocation mi = ast.newMethodInvocation();
                    mi.setName(ast.newSimpleName("baseTopic"));
                    mi.arguments().add(newStringLiteral(ast, this.api.getBaseTopic()));
                    body.statements().add(ast.newExpressionStatement(mi));
                }

                return md;
            });

        });
    }

    @SuppressWarnings("unchecked")
    private MethodDeclaration createReturnLatestVersionService(final Map.Entry<String, VersionedService> latestEntry, final AST ast) {
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
    private void renderServices() {
        for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : this.serviceDefinitions.getVersions().entrySet()) {

            final String version = makeVersion(versionEntry.getKey());
            final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName(version), this.options.getCharacterSet(), type -> null);

            for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {
                builder.createType(createServiceTypeInformation(serviceEntry), true, false, b -> {

                    for (final Topic topic : serviceEntry.getValue()) {
                        b.createMethod((ast, cu) -> {

                            final TopicInformation ti = this.serviceDefinitions.getTopics().get(topic);

                            // new method

                            final MethodDeclaration md = ast.newMethodDeclaration();
                            md.setName(ast.newSimpleName(makeTopicMethodName(ti)));

                            // return type

                            // set return type

                            md.setReturnType2(evalEventMethodType(ast, topic, this.context));

                            // assign annotation

                            final NormalAnnotation an = ast.newNormalAnnotation();
                            an.setTypeName(ast.newName(TOPIC_ANN_TYPE_NAME));
                            an.values().add(newKeyValueString(ast, "name", topic.getName()));
                            if (topic.getPublish() != null) {
                                an.values().add(newKeyValueClass(ast, "publish", messageTypeName(topic.getPublish(), this.context)));
                            }
                            if (topic.getSubscribe() != null) {
                                an.values().add(newKeyValueClass(ast, "subscribe", messageTypeName(topic.getSubscribe(), this.context)));
                            }
                            md.modifiers().add(an);

                            // make public

                            makePublic(md);

                            // return

                            return md;

                        });

                    }

                });
            }

        }
    }

    public static TypeInformation createServiceTypeInformation(final Map.Entry<String, List<Topic>> serviceEntry) {
        return new TypeInformation(asTypeName(serviceEntry.getKey()), null, null);
    }

    @SuppressWarnings("unchecked")
    public static ParameterizedType evalEventMethodType(final AST ast, final Topic topic, final Context context) {

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
            type.typeArguments().add(ast.newSimpleType(ast.newName(messageTypeName(pubMsg, context))));
        }
        if (subMsg != null) {
            type.typeArguments().add(ast.newSimpleType(ast.newName(messageTypeName(subMsg, context))));
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

    public static String messageTypeName(final MessageReference message, final Context context) {
        return context.fullQualifiedName("messages") + "." + PackageTypeBuilder.asTypeName(message.getName());
    }

    public static String makeTopicMethodName(final TopicInformation ti) {

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

    private void generateMessages() {
        final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName("messages"), this.options.getCharacterSet(), this::lookupType);

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

        @SuppressWarnings("unchecked")
        final Consumer<TypeDeclaration> typeCustomizer = td -> {
            final AST ast = td.getAST();

            final ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast.newName(MESSAGE_IFACE_TYPE_NAME)));
            type.typeArguments().add(ast.newSimpleType(ast.newName(ti.getName() + ".Payload")));

            td.superInterfaceTypes().add(type);
        };

        builder.createType(ti, typeCustomizer, b -> {

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

        final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName("types"), this.options.getCharacterSet(), typeName -> {
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
        builder.createEnum(new TypeInformation(asTypeName(type.getName()), type.getTitle(), type.getDescription()), type.getLiterals(),
                (literal, decl) -> fireExtensions(extension -> extension.createdEnumLiteral(literal, decl)), true);
    }

    private <T> void fireExtensions(final Consumer<GeneratorExtension> consumer) {
        for (final GeneratorExtension extension : this.extensions) {
            consumer.accept(extension);
        }
    }

    private <T> void fireExtensions(final T literal, final BiConsumer<GeneratorExtension, T> consumer) {
        for (final GeneratorExtension extension : this.extensions) {
            consumer.accept(extension, literal);
        }
    }

    private String packageName(final String local) {
        String base = this.options.getBasePackage();
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

        PackageTypeBuilder.createCompilationUnit(this.options.getTargetPath(), packageName(null), "package-info", this.options.getCharacterSet(), (ast, cu) -> {
            final Information info = this.api.getInformation();

            final Javadoc doc = ast.newJavadoc();

            if (info.getTitle() != null) {
                final TagElement tag = ast.newTagElement();
                tag.fragments().add(JDTHelper.newText(ast, info.getTitle()));
                doc.tags().add(tag);
            }

            if (info.getVersion() != null) {
                final TagElement version = ast.newTagElement();
                version.setTagName("@version");
                version.fragments().add(JDTHelper.newText(ast, info.getVersion()));
                doc.tags().add(version);
            }

            cu.getPackage().setJavadoc(doc);

        });
    }
}
