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
import java.util.stream.Collectors;
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

import de.dentrassi.asyncapi.ArrayType;
import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.CoreType;
import de.dentrassi.asyncapi.EnumType;
import de.dentrassi.asyncapi.Information;
import de.dentrassi.asyncapi.Message;
import de.dentrassi.asyncapi.MessageReference;
import de.dentrassi.asyncapi.ObjectType;
import de.dentrassi.asyncapi.ParentableType;
import de.dentrassi.asyncapi.Property;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.Type;
import de.dentrassi.asyncapi.TypeReference;
import de.dentrassi.asyncapi.generator.java.ServiceDefinitions.VersionedService;
import de.dentrassi.asyncapi.generator.java.util.JDTHelper;

public class Generator {

    private static final String TYPE_NAME_CONNECTOR = "de.dentrassi.asyncapi.Connector";

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

        public String fullQualifiedName(String... localName);

        public ServiceDefinitions getServiceDefinitions();
    }

    private static final String TYPE_NAME_MESSAGE_INTERFACE = "de.dentrassi.asyncapi.Message";

    private static final String TYPE_NAME_PUBSUB_CLASS = "de.dentrassi.asyncapi.PublishSubscribe";

    private static final String TYPE_NAME_SUB_CLASS = "de.dentrassi.asyncapi.Subscribe";

    private static final String TYPE_NAME_PUB_CLASS = "de.dentrassi.asyncapi.Publish";

    private static final String TYPE_NAME_TOPIC_ANN = "de.dentrassi.asyncapi.Topic";

    private static final String TYPE_NAME_ABSTRACT_CONNECTOR_BUILDER = "de.dentrassi.asyncapi.Connector.AbstractBuilder";

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
        public String fullQualifiedName(final String... localName) {
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
        renderServices(ConnectorType.CLIENT);
        renderServices(ConnectorType.SERVER);
        renderConnector(ConnectorType.CLIENT);
        renderConnector(ConnectorType.SERVER);
    }

    private TypeBuilder createTypeBuilder(final String... localPackageName) {
        return new PackageTypeBuilder(this.options.getTargetPath(), packageName(localPackageName), this.options.getCharacterSet(), type -> null, this::lookupType);
    }

    private void renderConnector(final ConnectorType connectorType) {

        final TypeBuilder builder = createTypeBuilder();

        final Consumer<TypeDeclaration> typeCustomizer = TypeBuilder.asInterface(true) //
                .andThen(TypeBuilder.superInterfaces(Arrays.asList(TYPE_NAME_CONNECTOR)));

        builder.createType(new TypeInformation(connectorType.getSimpleTypeName(), null, null), typeCustomizer, b -> {

            renderDefaultConnectorBuilder(b, connectorType);

            for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : this.serviceDefinitions.getVersions().entrySet()) {
                final String version = makeVersion(versionEntry.getKey());

                b.createType(new TypeInformation(version.toUpperCase(), null, null), true, false, vb -> {

                    for (final Map.Entry<String, List<Topic>> serviceEntry : versionEntry.getValue().entrySet()) {

                        final TypeInformation serviceType = createServiceTypeInformation(serviceEntry);

                        vb.createMethod((ast, cu) -> {
                            final MethodDeclaration md = ast.newMethodDeclaration();
                            md.setName(ast.newSimpleName(PackageTypeBuilder.asPropertyName(serviceType.getName())));
                            md.setReturnType2(ast.newSimpleType(ast.newName(packageName(connectorType.getPackageName(), version, serviceType.getName()))));
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
                    return createReturnLatestVersionService(latestEntry, ast, connectorType);
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
    private void renderDefaultConnectorBuilder(final TypeBuilder builder, final ConnectorType connectorType) {

        Consumer<TypeDeclaration> typeCustomizer = td -> {
            final AST ast = td.getAST();

            final SimpleType st = ast.newSimpleType(ast.newName(TYPE_NAME_ABSTRACT_CONNECTOR_BUILDER));

            td.setSuperclassType(parametrizeSimple(st, "B", "C"));
        };

        typeCustomizer = typeCustomizer.andThen(td -> {
            final AST ast = td.getAST();

            final TypeParameter b = ast.newTypeParameter();
            b.setName(ast.newSimpleName("B"));
            b.typeBounds().add(parametrizeSimple(ast.newSimpleType(ast.newSimpleName("Builder")), "B", "C"));

            final TypeParameter c = ast.newTypeParameter();
            c.setName(ast.newSimpleName("C"));
            c.typeBounds().add(ast.newSimpleType(ast.newSimpleName(connectorType.getSimpleTypeName())));

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
    private MethodDeclaration createReturnLatestVersionService(final Map.Entry<String, VersionedService> latestEntry, final AST ast, final ConnectorType connectorType) {
        final String version = makeVersion(latestEntry.getValue().getVersion().toString());
        final String serviceType = packageName(connectorType.getPackageName(), version, latestEntry.getValue().getType().getName());

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
    private void renderServices(final ConnectorType connectorType) {
        for (final Map.Entry<String, Map<String, List<Topic>>> versionEntry : this.serviceDefinitions.getVersions().entrySet()) {

            final String packageName = connectorType.getPackageName();
            final String version = makeVersion(versionEntry.getKey());
            final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName(packageName, version), this.options.getCharacterSet(), type -> null,
                    this::lookupType);

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

                            md.setReturnType2(evalEventMethodType(ast, topic, this.context, connectorType));

                            // assign annotation

                            final NormalAnnotation an = ast.newNormalAnnotation();
                            an.setTypeName(ast.newName(TYPE_NAME_TOPIC_ANN));
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
    public static ParameterizedType evalEventMethodType(final AST ast, final Topic topic, final Context context, final ConnectorType connectorType) {

        final MessageReference pubMsg = connectorType.getPublish(topic);
        final MessageReference subMsg = connectorType.getSubscribe(topic);

        if (pubMsg == null && subMsg == null) {
            return null;
        }

        final SimpleType eventType;

        if (pubMsg != null && subMsg != null) {
            eventType = ast.newSimpleType(ast.newName(TYPE_NAME_PUBSUB_CLASS));
        } else if (pubMsg != null) {
            eventType = ast.newSimpleType(ast.newName(TYPE_NAME_PUB_CLASS));
        } else {
            eventType = ast.newSimpleType(ast.newName(TYPE_NAME_SUB_CLASS));
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
        final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName("messages"), this.options.getCharacterSet(), this::resolveTypeName,
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

        final TypeInformation ti = new TypeInformation(asTypeName(message.getName()), message.getSummary(), message.getDescription());

        //         final String payloadTypeName = PackageTypeBuilder.asTypeName(message.getPayload().getName());

        @SuppressWarnings("unchecked")
        final Consumer<TypeDeclaration> typeCustomizer = td -> {
            final AST ast = td.getAST();

            final ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast.newName(TYPE_NAME_MESSAGE_INTERFACE)));
            type.typeArguments().add(ast.newSimpleType(ast.newName(ti.getName() + ".Payload")));

            td.superInterfaceTypes().add(type);
        };

        final TypeReference payloadType = message.getPayload();

        builder.createType(ti, typeCustomizer, b -> {

            if (payloadType instanceof ObjectType) {

                generateType(b, (Type) payloadType);

                b.createProperty(new PropertyInformation((Type) payloadType, "payload", "Message payload", null));

            } else if (payloadType instanceof CoreType) {

                b.createProperty(new PropertyInformation((CoreType) message.getPayload(), "payload", "Message payload", null));

            } else if (payloadType.getClass().equals(TypeReference.class)) {
                b.createProperty(new PropertyInformation(lookupType(payloadType), "payload", "Message payload", null));
            } else {
                throw new IllegalStateException("Unsupported payload type: " + message.getPayload().getClass().getName());
            }

        });
    }

    private void generateTypes() {

        final TypeBuilder builder = new PackageTypeBuilder(this.options.getTargetPath(), packageName("types"), this.options.getCharacterSet(), this::resolveTypeName,
                this::lookupType);

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

    private static String toPrimitives(final CoreType type) {
        final String typeName = type.getJavaType().getName();

        switch (typeName) {
        case "java.lang.Boolean":
            return "boolean";
        case "java.lang.Integer":
            return "int";
        case "java.lang.Long":
            return "long";
        case "java.lang.Float":
            return "float";
        case "java.lang.Short":
            return "short";
        case "java.lang.Byte":
            return "byte";
        case "java.lang.Character":
            return "char";
        case "java.lang.Double":
            return "double";
        default:
            return typeName;
        }

    }

    private String resolveTypeName(final Type type) {
        return resolveTypeName(type, false);
    }

    private String resolveTypeName(final TypeReference typeRef, final boolean allowPrimitives) {
        if (typeRef instanceof ObjectType || typeRef instanceof EnumType) {

            return resolveParentableTypeName(typeRef);

        } else if (typeRef instanceof CoreType) {

            if (allowPrimitives) {
                return toPrimitives((CoreType) typeRef);
            } else {
                return ((CoreType) typeRef).getJavaType().getName();
            }

        } else if (typeRef instanceof ArrayType) {

            return resolveTypeName(((ArrayType) typeRef).getItemType(), false);

        } else {

            return resolveTypeName(lookupType(typeRef.getName()), allowPrimitives);

        }
    }

    private String resolveParentableTypeName(final TypeReference typeRef) {
        final List<String> full = new LinkedList<>();

        full.add(typeRef.getNamespace());

        if (typeRef instanceof ParentableType) {
            for (final String parent : ((ParentableType) typeRef).getParents()) {
                full.add(asTypeName(parent));
            }
        }

        full.add(asTypeName(typeRef.getName()));

        return packageName(String.join(".", full));
    }

    private Type lookupType(final TypeReference typeRef) {
        if (typeRef instanceof Type) {
            return (Type) typeRef;
        }

        return lookupType(typeRef.getName());
    }

    private void generateProperty(final Property property, final TypeBuilder builder) {

        // build local type

        final TypeReference type = property.getType();
        if (type instanceof Type) {
            generateType(builder, (Type) type);
        }

        // generate property

        final String name = PackageTypeBuilder.asPropertyName(property.getName());

        final String summary = property.getDescription(); // FIXME: chase description
        final String description = null; // FIXME: chase description

        builder.createProperty(new PropertyInformation(lookupType(type), name, summary, description));
    }

    private void generateEnum(final EnumType type, final TypeBuilder builder) {
        builder.createEnum(new TypeInformation(asTypeName(type.getName()), type.getTitle(), type.getDescription()), type.getLiterals(),
                (literal, decl) -> fireExtensions(extension -> extension.createdEnumLiteral(literal, decl)), true);
    }

    protected <T> void fireExtensions(final Consumer<GeneratorExtension> consumer) {
        for (final GeneratorExtension extension : this.extensions) {
            consumer.accept(extension);
        }
    }

    protected <T> void fireExtensions(final T literal, final BiConsumer<GeneratorExtension, T> consumer) {
        for (final GeneratorExtension extension : this.extensions) {
            consumer.accept(extension, literal);
        }
    }

    private String packageName(final String... local) {

        Stream<String> full;

        if (this.options.getBasePackage() != null && !this.options.getBasePackage().isEmpty()) {
            full = Stream.of(this.options.getBasePackage());
        } else if (this.api.getBaseTopic() != null && !this.api.getBaseTopic().isEmpty()) {
            full = Stream.of(this.api.getBaseTopic());
        } else {
            full = Stream.empty();
        }

        if (local != null) {
            full = Stream.concat(full, Arrays.stream(local));
        }

        return full.collect(Collectors.joining("."));
    }

    @SuppressWarnings("unchecked")
    private void generateRoot() throws IOException {

        PackageTypeBuilder.createCompilationUnit(this.options.getTargetPath(), packageName(), "package-info", this.options.getCharacterSet(), (ast, cu) -> {
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
