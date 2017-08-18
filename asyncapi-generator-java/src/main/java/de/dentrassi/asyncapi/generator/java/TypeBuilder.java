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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import de.dentrassi.asyncapi.generator.java.util.JDTHelper;

public interface TypeBuilder {

    public default void createType(final TypeInformation type, final boolean iface, final boolean serializable, final Consumer<TypeBuilder> consumer) {
        createType(type, defaultTypeCustomizer(iface, serializable, true), consumer);
    }

    public void createType(TypeInformation type, Consumer<TypeDeclaration> typeCustomizer, Consumer<TypeBuilder> consumer);

    public void createEnum(TypeInformation type, Set<String> literals, BiConsumer<String, EnumConstantDeclaration> constantCustomizer, boolean withOriginalValues);

    public void createProperty(PropertyInformation property);

    public void createBodyContent(BiFunction<AST, CompilationUnit, List<ASTNode>> consumer);

    public default void createMethod(final BiFunction<AST, CompilationUnit, MethodDeclaration> consumer) {
        createBodyContent((ast, cu) -> {
            return Collections.singletonList(consumer.apply(ast, cu));
        });
    }

    @SuppressWarnings("unchecked")
    public static Consumer<TypeDeclaration> defaultTypeCustomizer(final boolean iface, final boolean serializable, final boolean makeStatic) {
        Consumer<TypeDeclaration> typeCustomizer = TypeBuilder.asInterface(iface);

        typeCustomizer = typeCustomizer.andThen(TypeBuilder.superInterfaces(serializable ? Collections.singletonList("java.io.Serializable") : Collections.emptyList()));

        if (makeStatic) {
            typeCustomizer = typeCustomizer.andThen(JDTHelper::makeStatic);
        }

        if (serializable && !iface) {
            typeCustomizer = typeCustomizer.andThen(td -> {

                final AST ast = td.getAST();

                final org.eclipse.jdt.core.dom.Type longType = ast.newPrimitiveType(PrimitiveType.LONG);
                final NumberLiteral initializer = ast.newNumberLiteral();
                initializer.setToken("1L");
                final FieldDeclaration sf = JDTHelper.createField(ast, longType, "serialVersionUID", initializer, ModifierKeyword.PRIVATE_KEYWORD, ModifierKeyword.STATIC_KEYWORD,
                        ModifierKeyword.FINAL_KEYWORD);

                td.bodyDeclarations().add(sf);
            });
        }
        return typeCustomizer;
    }

    @SuppressWarnings("unchecked")
    public static Consumer<TypeDeclaration> superInterfaces(final List<String> types) {
        return td -> {

            final AST ast = td.getAST();

            for (final String name : types) {
                final org.eclipse.jdt.core.dom.Type type = ast.newSimpleType(ast.newName(name));
                td.superInterfaceTypes().add(type);
            }

        };
    }

    public static Consumer<TypeDeclaration> asInterface(final boolean value) {
        return td -> td.setInterface(value);
    }

    public static Consumer<TypeDeclaration> superClass(final String name) {
        return td -> {

            final AST ast = td.getAST();

            final org.eclipse.jdt.core.dom.Type type = ast.newSimpleType(ast.newName(name));
            td.setSuperclassType(type);

        };
    }

    @SuppressWarnings("unchecked")
    public static Consumer<TypeDeclaration> make(final ModifierKeyword... keywords) {
        return td -> {
            final AST ast = td.getAST();
            for (final ModifierKeyword keyword : keywords) {
                td.modifiers().add(ast.newModifier(keyword));
            }
        };
    }

}
