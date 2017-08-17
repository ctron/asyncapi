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

package de.dentrassi.asyncapi.generator.java.gson;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;

import de.dentrassi.asyncapi.generator.java.GeneratorExtension;
import de.dentrassi.asyncapi.generator.java.util.JDTHelper;

public class GsonGenerator implements GeneratorExtension {
    @SuppressWarnings("unchecked")
    @Override
    public void createdEnumLiteral(final String literal, final EnumConstantDeclaration enumConstantDeclaration) {
        final AST ast = enumConstantDeclaration.getAST();

        final NormalAnnotation an = ast.newNormalAnnotation();
        an.setTypeName(ast.newName("com.google.gson.annotations.SerializedName"));

        final MemberValuePair mvp = ast.newMemberValuePair();
        mvp.setName(ast.newSimpleName("value"));
        mvp.setValue(JDTHelper.newStringLiteral(ast, literal));

        an.values().add(mvp);

        enumConstantDeclaration.modifiers().add(an);
    }
}
