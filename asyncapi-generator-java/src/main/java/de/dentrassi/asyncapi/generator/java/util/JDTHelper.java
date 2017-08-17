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

package de.dentrassi.asyncapi.generator.java.util;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextElement;

@SuppressWarnings("unchecked")
public final class JDTHelper {
    private JDTHelper() {
    }

    public static TextElement newText(final AST ast, final String text) {
        final TextElement result = ast.newTextElement();
        result.setText(text);
        return result;
    }

    public static StringLiteral newStringLiteral(final AST ast, final String value) {
        final StringLiteral result = ast.newStringLiteral();
        result.setLiteralValue(value);
        return result;
    }

    public static void make(final AST ast, final List<? super Object> decl, final ModifierKeyword... keywords) {
        for (final ModifierKeyword keyword : keywords) {
            decl.add(ast.newModifier(keyword));
        }
    }

    public static void make(final BodyDeclaration decl, final ModifierKeyword... keywords) {
        make(decl.getAST(), decl.modifiers(), keywords);
    }

    public static void make(final SingleVariableDeclaration decl, final ModifierKeyword... keywords) {
        make(decl.getAST(), decl.modifiers(), keywords);
    }

    public static void makeStatic(final BodyDeclaration decl) {
        make(decl, ModifierKeyword.STATIC_KEYWORD);
    }

    public static void makeAbstract(final BodyDeclaration decl) {
        make(decl, ModifierKeyword.ABSTRACT_KEYWORD);
    }

    public static void makePublic(final BodyDeclaration decl) {
        make(decl, ModifierKeyword.PUBLIC_KEYWORD);
    }

    public static void makeProtected(final BodyDeclaration decl) {
        make(decl, ModifierKeyword.PROTECTED_KEYWORD);
    }

    public static void makePrivate(final BodyDeclaration decl) {
        make(decl, ModifierKeyword.PRIVATE_KEYWORD);
    }

    public static SingleVariableDeclaration createParameter(final AST ast, final String typeName, final String parameterName, final ModifierKeyword... keywords) {
        final SingleVariableDeclaration arg = ast.newSingleVariableDeclaration();
        arg.setName(ast.newSimpleName(parameterName));
        arg.setType(ast.newSimpleType(ast.newName(typeName)));
        make(arg, keywords);
        return arg;
    }

    public static void addSimpleAnnotation(final BodyDeclaration decl, final String name) {
        final AST ast = decl.getAST();
        final MarkerAnnotation ann = ast.newMarkerAnnotation();
        ann.setTypeName(ast.newName(name));
        decl.modifiers().add(ann);
    }

    public static CatchClause createCatchBlock(final AST ast, final String typeName, final Block catchBlock) {
        final CatchClause cc = ast.newCatchClause();
        cc.setException(JDTHelper.createParameter(ast, typeName, "e", ModifierKeyword.FINAL_KEYWORD));
        cc.setBody(catchBlock);
        return cc;
    }

}
