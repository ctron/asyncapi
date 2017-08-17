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

package de.dentrassi.asyncapi.generator.java.jms;

import static de.dentrassi.asyncapi.generator.java.util.JDTHelper.createCatchBlock;
import static de.dentrassi.asyncapi.generator.java.util.JDTHelper.makePrivate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.Topic;
import de.dentrassi.asyncapi.generator.java.Generator;
import de.dentrassi.asyncapi.generator.java.Generator.Context;
import de.dentrassi.asyncapi.generator.java.Generator.Options;
import de.dentrassi.asyncapi.generator.java.GeneratorExtension;
import de.dentrassi.asyncapi.generator.java.TypeBuilder;
import de.dentrassi.asyncapi.generator.java.TypeInformation;
import de.dentrassi.asyncapi.generator.java.util.JDTHelper;
import de.dentrassi.asyncapi.generator.java.util.Names;

public class JmsClientGeneratorExtension implements GeneratorExtension {

    private static final String TYPE_NAME_ABSTRACT_JMS_CLIENT = "de.dentrassi.asyncapi.jms.AbstractJmsClient";

    @Override
    public void generate(final AsyncApi api, final Options options, final Context context) {
        final TypeBuilder builder = context.createTypeBuilder("jms.client");

        final Consumer<TypeDeclaration> typeCustomizer = TypeBuilder //
                .superClass(TYPE_NAME_ABSTRACT_JMS_CLIENT) //
                .andThen(TypeBuilder.superInterfaces(Arrays.asList(context.fullQualifiedName("Client"))));

        builder.createType(new TypeInformation("JmsClient", null, null), typeCustomizer, b -> {

            createBuilderType(b);
            createNewBuilderMethod(b);

            createServiceFields(b, context);
            createConstructor(b, context);
            createVersions(b, context);

        });

        createServiceClasses(context);
    }

    @SuppressWarnings("unchecked")
    private void createServiceClasses(final Context context) {
        for (final Map.Entry<String, Map<String, List<Topic>>> entry : context.getServiceDefinitions().getVersions().entrySet()) {

            final String version = Names.makeVersion(entry.getKey());

            for (final Map.Entry<String, List<Topic>> serviceEntry : entry.getValue().entrySet()) {
                final String serviceName = Names.toCamelCase(serviceEntry.getKey(), false);
                final String serviceTypeName = makeServiceType(context, version, serviceEntry, false);

                final String implName = Names.toCamelCase(serviceName, true) + "Impl";

                final TypeBuilder builder = context.createTypeBuilder("jms.client." + version);

                final Consumer<TypeDeclaration> typeCustomizer = TypeBuilder.superInterfaces(Arrays.asList(serviceTypeName)) //
                        .andThen(TypeBuilder.superClass("de.dentrassi.asyncapi.jms.AbstractJmsServiceImpl"));

                builder.createType(new TypeInformation(implName, null, null), typeCustomizer, b -> {

                    createServiceConstructor(b, implName);

                    for (final Topic topic : serviceEntry.getValue()) {
                        final String name = Generator.makeTopicMethodName(context.getServiceDefinitions().getTopics().get(topic));

                        b.createMethod((ast, cu) -> {

                            final MethodDeclaration md = ast.newMethodDeclaration();

                            JDTHelper.makePublic(md);
                            JDTHelper.addSimpleAnnotation(md, "Override");

                            md.setName(ast.newSimpleName(name));
                            md.setReturnType2(Generator.evalEventMethodType(ast, topic, context));

                            // body

                            final Block body = ast.newBlock();
                            md.setBody(body);

                            // return

                            final ReturnStatement ret = ast.newReturnStatement();

                            if (topic.getPublish() != null && topic.getSubscribe() != null) {
                                ret.setExpression(aggregate(ast, publisher(ast, topic.getName()),
                                        subscriber(ast, topic.getName(), Generator.messageTypeName(topic.getSubscribe(), context))));
                            } else if (topic.getPublish() != null) {
                                ret.setExpression(publisher(ast, topic.getName()));
                            } else if (topic.getSubscribe() != null) {
                                ret.setExpression(subscriber(ast, topic.getName(), Generator.messageTypeName(topic.getSubscribe(), context)));
                            }

                            body.statements().add(ret);

                            return md;

                        });
                    }

                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Expression aggregate(final AST ast, final Expression publisher, final Expression subscriber) {
        // new AggregatePublishSubscriber<>(publish, subscribe)

        final SimpleType type = ast.newSimpleType(ast.newName("de.dentrassi.asyncapi.util.AggregatePublishSubscriber"));
        final ParameterizedType pt = ast.newParameterizedType(type);

        final ClassInstanceCreation cic = ast.newClassInstanceCreation();
        cic.setType(pt);

        cic.arguments().add(publisher);
        cic.arguments().add(subscriber);

        return cic;
    }

    @SuppressWarnings("unchecked")
    private Expression subscriber(final AST ast, final String topicName, final String messageTypeName) {
        // createSubscriber(TOPIC_EVENT_DEVICE_ADDED, DeviceEvent.class, DeviceEvent.Payload.class)
        final MethodInvocation mi = ast.newMethodInvocation();
        mi.setName(ast.newSimpleName("createSubscriber"));

        mi.arguments().add(JDTHelper.newStringLiteral(ast, topicName));

        {
            final TypeLiteral tl = ast.newTypeLiteral();
            tl.setType(ast.newSimpleType(ast.newName(messageTypeName)));

            mi.arguments().add(tl);
        }

        {
            final TypeLiteral tl = ast.newTypeLiteral();
            tl.setType(ast.newSimpleType(ast.newName(messageTypeName + ".Payload")));

            mi.arguments().add(tl);
        }

        return mi;
    }

    @SuppressWarnings("unchecked")
    private Expression publisher(final AST ast, final String topicName) {
        // createPublisher(TOPIC_EVENT_DEVICE_UPDATE)

        final MethodInvocation mi = ast.newMethodInvocation();
        mi.setName(ast.newSimpleName("createPublisher"));
        mi.arguments().add(JDTHelper.newStringLiteral(ast, topicName));

        return mi;
    }

    @SuppressWarnings("unchecked")
    private void createServiceConstructor(final TypeBuilder b, final String implName) {
        /*
         * public DevicesImpl(final Connection connection, final Executor executor, final JmsPayloadFormat payloadFormat, final String baseTopic) {
         *   super(connection, executor, payloadFormat, baseTopic);
         * }
         */

        b.createMethod((ast, cu) -> {
            final MethodDeclaration md = ast.newMethodDeclaration();

            md.setConstructor(true);
            md.setName(ast.newSimpleName(implName));
            JDTHelper.makePublic(md);

            // arguments

            md.parameters().add(JDTHelper.createParameter(ast, "javax.jms.Connection", "connection", ModifierKeyword.FINAL_KEYWORD));
            md.parameters().add(JDTHelper.createParameter(ast, "java.util.concurrent.Executor", "executor", ModifierKeyword.FINAL_KEYWORD));
            md.parameters().add(JDTHelper.createParameter(ast, "de.dentrassi.asyncapi.jms.JmsPayloadFormat", "payloadFormat", ModifierKeyword.FINAL_KEYWORD));
            md.parameters().add(JDTHelper.createParameter(ast, "String", "baseTopic", ModifierKeyword.FINAL_KEYWORD));

            // body

            final Block body = ast.newBlock();
            md.setBody(body);

            // super call

            final SuperConstructorInvocation sci = ast.newSuperConstructorInvocation();
            body.statements().add(sci);

            sci.arguments().add(ast.newSimpleName("connection"));
            sci.arguments().add(ast.newSimpleName("executor"));
            sci.arguments().add(ast.newSimpleName("payloadFormat"));
            sci.arguments().add(ast.newSimpleName("baseTopic"));

            return md;
        });
    }

    @SuppressWarnings("unchecked")
    private void createVersions(final TypeBuilder builder, final Context context) {

        for (final Map.Entry<String, Map<String, List<Topic>>> entry : context.getServiceDefinitions().getVersions().entrySet()) {

            final String version = Names.makeVersion(entry.getKey());
            final String versionTypeName = version.toUpperCase();

            builder.createMethod((ast, cu) -> {

                // create v1() method

                final MethodDeclaration md = ast.newMethodDeclaration();
                md.setName(ast.newSimpleName(version));
                md.setReturnType2(ast.newSimpleType(ast.newName(versionTypeName)));

                JDTHelper.addSimpleAnnotation(md, "Override");
                JDTHelper.makePublic(md);

                // body

                final Block body = ast.newBlock();
                md.setBody(body);

                // return

                final ReturnStatement ret = ast.newReturnStatement();
                body.statements().add(ret);

                // new V1 type

                final AnonymousClassDeclaration cd = ast.newAnonymousClassDeclaration();

                final ClassInstanceCreation ci = ast.newClassInstanceCreation();
                ci.setAnonymousClassDeclaration(cd);
                ci.setType(ast.newSimpleType(ast.newName(versionTypeName)));

                ret.setExpression(ci);

                // create V1 methods

                for (final Map.Entry<String, List<Topic>> serviceEntry : entry.getValue().entrySet()) {

                    final String serviceName = Names.toCamelCase(serviceEntry.getKey(), false);
                    final String serviceInstanceField = version + Names.toCamelCase(serviceName, true);

                    final String serviceTypeName = makeServiceType(context, version, serviceEntry, false);

                    final MethodDeclaration smd = ast.newMethodDeclaration();
                    smd.setName(ast.newSimpleName(serviceName));
                    smd.setReturnType2(ast.newSimpleType(ast.newName(serviceTypeName)));

                    JDTHelper.addSimpleAnnotation(smd, "Override");
                    JDTHelper.makePublic(smd);

                    cd.bodyDeclarations().add(smd);

                    final Block sbody = ast.newBlock();
                    smd.setBody(sbody);

                    // > return JmsClient.this.v1Service

                    final ReturnStatement ret2 = ast.newReturnStatement();
                    sbody.statements().add(ret2);

                    final ThisExpression te = ast.newThisExpression();
                    te.setQualifier(ast.newSimpleName("JmsClient"));

                    final FieldAccess fa = ast.newFieldAccess();
                    fa.setExpression(te);
                    fa.setName(ast.newSimpleName(serviceInstanceField));

                    ret2.setExpression(fa);
                }

                return md;
            });

        }

    }

    @SuppressWarnings("unchecked")
    private void createBuilderType(final TypeBuilder builder) {

        final Consumer<TypeDeclaration> typeCustomizer = //
                TypeBuilder.make(ModifierKeyword.STATIC_KEYWORD).andThen(td -> {

                    final AST ast = td.getAST();
                    final SimpleType type = ast.newSimpleType(ast.newName(TYPE_NAME_ABSTRACT_JMS_CLIENT + ".Builder"));

                    final ParameterizedType pt = ast.newParameterizedType(type);
                    pt.typeArguments().add(ast.newSimpleType(ast.newSimpleName("JmsClient")));

                    td.setSuperclassType(pt);

                });

        builder.createType(new TypeInformation("Builder", null, null), typeCustomizer, b -> {
            b.createMethod((ast, cu) -> {

                final MethodDeclaration md = ast.newMethodDeclaration();

                JDTHelper.addSimpleAnnotation(md, "Override");
                JDTHelper.makePublic(md);

                md.setName(ast.newSimpleName("build"));
                md.setReturnType2(ast.newSimpleType(ast.newName("JmsClient")));

                final TryStatement ts = ast.newTryStatement();

                // try

                final Block tryBlock = ast.newBlock();
                ts.setBody(tryBlock);

                final ReturnStatement ret = ast.newReturnStatement();
                final ClassInstanceCreation cir = ast.newClassInstanceCreation();
                cir.setType(ast.newSimpleType(ast.newName("JmsClient")));
                cir.arguments().add(ast.newThisExpression());

                ret.setExpression(cir);

                tryBlock.statements().add(ret);

                // catch

                final Block catchBlock = ast.newBlock();
                final ThrowStatement trs = ast.newThrowStatement();
                final ClassInstanceCreation cir2 = ast.newClassInstanceCreation();
                cir2.setType(ast.newSimpleType(ast.newSimpleName("RuntimeException")));
                cir2.arguments().add(ast.newSimpleName("e"));
                trs.setExpression(cir2);
                catchBlock.statements().add(trs);
                ts.catchClauses().add(createCatchBlock(ast, "Exception", catchBlock));

                // method body

                final Block body = ast.newBlock();
                body.statements().add(ts);

                md.setBody(body);

                return md;
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void createNewBuilderMethod(final TypeBuilder builder) {
        builder.createMethod((ast, cu) -> {
            final MethodDeclaration md = ast.newMethodDeclaration();

            md.setName(ast.newSimpleName("newBuilder"));
            JDTHelper.make(md, ModifierKeyword.PUBLIC_KEYWORD, ModifierKeyword.STATIC_KEYWORD);
            md.setReturnType2(ast.newSimpleType(ast.newSimpleName("Builder")));

            final Block body = ast.newBlock();
            md.setBody(body);

            final ClassInstanceCreation cir = ast.newClassInstanceCreation();
            cir.setType(ast.newSimpleType(ast.newName("Builder")));

            final ReturnStatement ret = ast.newReturnStatement();
            ret.setExpression(cir);

            body.statements().add(ret);

            return md;
        });
    }

    private void createServiceFields(final TypeBuilder builder, final Context context) {
        for (final Map.Entry<String, Map<String, List<Topic>>> entry : context.getServiceDefinitions().getVersions().entrySet()) {

            final String version = Names.makeVersion(entry.getKey());

            for (final Map.Entry<String, List<Topic>> serviceEntry : entry.getValue().entrySet()) {
                final String serviceName = Names.toCamelCase(serviceEntry.getKey(), false);
                final String serviceInstanceField = version + Names.toCamelCase(serviceName, true);
                final String serviceTypeName = makeServiceType(context, version, serviceEntry, true);

                builder.createBodyContent((ast, cu) -> {

                    final VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
                    vdf.setName(ast.newSimpleName(serviceInstanceField));
                    final FieldDeclaration fd = ast.newFieldDeclaration(vdf);

                    fd.setType(ast.newSimpleType(ast.newName(serviceTypeName)));

                    JDTHelper.make(fd, ModifierKeyword.PRIVATE_KEYWORD, ModifierKeyword.FINAL_KEYWORD);

                    return fd;

                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createConstructor(final TypeBuilder builder, final Context context) {
        builder.createMethod((ast, cu) -> {
            final MethodDeclaration md = ast.newMethodDeclaration();

            md.setConstructor(true);
            md.setName(ast.newSimpleName("JmsClient"));
            makePrivate(md);

            md.parameters().add(JDTHelper.createParameter(ast, "Builder", "builder", ModifierKeyword.FINAL_KEYWORD));

            final Block body = ast.newBlock();
            md.setBody(body);

            final SuperConstructorInvocation s = ast.newSuperConstructorInvocation();
            body.statements().add(s);

            s.arguments().add(ast.newSimpleName("builder"));

            // add throws
            md.thrownExceptionTypes().add(ast.newSimpleType(ast.newName("javax.jms.JMSException")));

            // add service field instances

            for (final Map.Entry<String, Map<String, List<Topic>>> entry : context.getServiceDefinitions().getVersions().entrySet()) {

                final String version = Names.makeVersion(entry.getKey());

                for (final Map.Entry<String, List<Topic>> serviceEntry : entry.getValue().entrySet()) {
                    final String serviceName = Names.toCamelCase(serviceEntry.getKey(), false);
                    final String serviceInstanceField = version + Names.toCamelCase(serviceName, true);
                    final String serviceTypeName = makeServiceType(context, version, serviceEntry, true);

                    createServiceInstance(md, serviceInstanceField, serviceTypeName);

                }
            }

            // return

            return md;
        });
    }

    private String makeServiceType(final Context context, final String version, final Map.Entry<String, List<Topic>> serviceEntry, final boolean implementation) {
        if (implementation) {
            return context.fullQualifiedName("jms.client." + version) + "." + Names.toCamelCase(serviceEntry.getKey() + "Impl", true);
        } else {
            return context.fullQualifiedName(version) + "." + Names.toCamelCase(serviceEntry.getKey(), true);
        }
    }

    @SuppressWarnings("unchecked")
    private void createServiceInstance(final MethodDeclaration md, final String serviceInstanceField, final String serviceTypeName) {
        final AST ast = md.getAST();
        final Block body = md.getBody();

        final Assignment as = ast.newAssignment();

        // assign to

        final FieldAccess fa = ast.newFieldAccess();
        fa.setName(ast.newSimpleName(serviceInstanceField));
        fa.setExpression(ast.newThisExpression());
        as.setLeftHandSide(fa);

        // create instance

        final ClassInstanceCreation cic = ast.newClassInstanceCreation();
        cic.setType(ast.newSimpleType(ast.newName(serviceTypeName)));

        // parameters

        // new DevicesImpl(this.connection, this.executor, builder.payloadFormat(), builder.baseTopic());

        {
            final FieldAccess t = ast.newFieldAccess();
            t.setExpression(ast.newThisExpression());
            t.setName(ast.newSimpleName("connection"));
            cic.arguments().add(t);
        }
        {
            final FieldAccess t = ast.newFieldAccess();
            t.setExpression(ast.newThisExpression());
            t.setName(ast.newSimpleName("executor"));
            cic.arguments().add(t);
        }
        {
            final MethodInvocation mi = ast.newMethodInvocation();
            mi.setExpression(ast.newSimpleName("builder"));
            mi.setName(ast.newSimpleName("payloadFormat"));
            cic.arguments().add(mi);
        }
        {
            final MethodInvocation mi = ast.newMethodInvocation();
            mi.setExpression(ast.newSimpleName("builder"));
            mi.setName(ast.newSimpleName("baseTopic"));
            cic.arguments().add(mi);
        }

        // set

        as.setRightHandSide(cic);

        // add

        body.statements().add(ast.newExpressionStatement(as));
    }

}
