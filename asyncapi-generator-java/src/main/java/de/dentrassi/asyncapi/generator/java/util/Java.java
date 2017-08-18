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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public final class Java {
    private Java() {
    }

    /**
     * Stream a list of {@link ASTNode}s
     *
     * @param nodes
     *            the nodes to stream
     * @return the stream
     */
    @SuppressWarnings("unchecked")
    public static Stream<ASTNode> stream(final List<?> nodes) {
        return ((List<ASTNode>) nodes).stream();
    }

    /**
     * Stream a list of {@link ASTNode}s from an {@link ASTNode} property
     * <p>
     * This can be used e.g. like the following:
     * </p>
     * 
     * <pre>
     * stream(node, TypeDeclaration.class, TypeDeclaration::bodyDeclaration)
     *         .findFirst()
     *         .get();
     * </pre>
     *
     * @param node
     *            the source node
     * @param clazz
     *            the required type of the source node
     * @param supplier
     *            the supplier of nodes
     * @param <T>
     *            the type of the {@link ASTNode}
     * @return a stream of nodes
     */
    public static <T extends ASTNode> Stream<ASTNode> stream(final ASTNode node, final Class<T> clazz, final Function<T, List<?>> supplier) {
        return stream(supplier.apply(clazz.cast(node)));
    }

    /**
     * Get the first node of a {@link TypeDeclaration#bodyDeclarations()}
     * <p>
     * <b>Note: </b> Fails of there is no first node.
     * </p>
     *
     * @param node
     *            the node to work on
     * @return the first node from the body declaration
     */
    public static ASTNode firstBodyDeclaration(final ASTNode node) {
        return stream(node, TypeDeclaration.class, TypeDeclaration::bodyDeclarations)
                .findFirst()
                .get();
    }

    @SuppressWarnings("unchecked")
    public static List<ASTNode> parse(final AST ast, final int kind, final String code, final Function<ASTNode, List<ASTNode>> extractor) {
        final List<ASTNode> result = parseInternal(ast, kind, code, node -> {
            if (extractor != null) {
                return extractor.apply(node);
            } else {
                return Collections.singletonList(node);
            }
        });

        return ASTNode.copySubtrees(ast, result);
    }

    public static ASTNode parseSingle(final AST ast, final int kind, final String code, final Function<ASTNode, ASTNode> extractor) {
        final ASTNode result = parseInternal(ast, kind, code, node -> {
            if (extractor != null) {
                return extractor.apply(node);
            } else {
                return node;
            }
        });

        return ASTNode.copySubtree(ast, result);
    }

    public static List<ASTNode> parseSingleList(final AST ast, final int kind, final String code, final Function<ASTNode, ASTNode> extractor) {
        final ASTNode result = parseSingle(ast, kind, code, extractor);

        if (result == null) {
            throw new IllegalStateException("No element parsed");
        }

        return Collections.singletonList(result);
    }

    public static <T> T parseInternal(final AST ast, final int kind, final String code, final Function<ASTNode, T> extractor) {
        final ASTParser parser = ASTParser.newParser(AST.JLS8);

        parser.setKind(kind);
        parser.setSource(code.toCharArray());

        final ASTNode node = parser.createAST(null);

        return extractor.apply(node);
    }

}
