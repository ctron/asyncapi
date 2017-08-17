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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Names {
    private Names() {
    }

    @FunctionalInterface
    private interface CharMapper {
        char map(char in);
    }

    @FunctionalInterface
    private interface CharConsumer {
        void consume(char in);
    }

    public static String toCamelCase(final String text, final boolean firstUpper) {
        return toFormat(text, words -> joinCamel(firstUpper, words));
    }

    public static String toLowerDash(final String text) {
        return toFormat(text, words -> joinDelimiter(words, String::toLowerCase, "-"));
    }

    public static String toUpperUnderscore(final String text) {
        return toFormat(text, words -> joinDelimiter(words, String::toUpperCase, "_"));
    }

    private static enum Type {
        UPPER, LOWER, NUMERIC, OTHER;
    }

    public static String toFormat(final String text, final Function<List<String>, String> target) {
        if (text == null) {
            return null;
        }

        final List<String> words = new LinkedList<>();
        final int len = text.length();

        Type lastType = Type.UPPER;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; i++) {
            final char c = text.charAt(i);

            final Type type = typeOf(c);

            if (lastType == Type.LOWER && (type == Type.UPPER || type == Type.NUMERIC)) {
                sb = split(sb, words);
                sb = append(sb, c);
            } else if (lastType == Type.NUMERIC && (type == Type.UPPER || type == Type.LOWER)) {
                sb = split(sb, words);
                sb = append(sb, c);
            } else if (type != Type.OTHER) {
                sb = append(sb, c);
            } else {
                // unknown type: split and ignore
                sb = split(sb, words);
            }

            lastType = type;
        }

        split(sb, words);

        return target.apply(words);
    }

    private static StringBuilder append(StringBuilder sb, final char c) {
        if (sb == null) {
            sb = new StringBuilder();
        }
        sb.append(c);
        return sb;
    }

    private static StringBuilder split(StringBuilder sb, final List<String> words) {
        if (sb != null) {
            words.add(sb.toString());
            sb = null;
        }
        return sb;
    }

    private static Type typeOf(final char c) {
        if (Character.isUpperCase(c)) {
            return Type.UPPER;
        } else if (Character.isDigit(c)) {
            return Type.NUMERIC;
        } else if (Character.isAlphabetic(c)) {
            return Type.LOWER;
        }
        return Type.OTHER;
    }

    private static String joinDelimiter(final List<String> words, Function<String, String> conversion, final String delimiter) {
        if (conversion == null) {
            conversion = str -> str;
        }

        return words.stream().map(conversion).collect(Collectors.joining(delimiter));
    }

    private static String joinCamel(final boolean firstUpper, final List<String> words) {
        CharMapper mapper = firstUpper ? Character::toUpperCase : Character::toLowerCase;
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {

            final int wlen = word.length();
            if (wlen < 1) {
                continue;
            }

            result.append(mapper.map(word.charAt(0)));

            if (wlen > 1) {
                result.append(word.substring(1).toLowerCase());
            }

            mapper = Character::toUpperCase;
        }
        return result.toString();
    }

    public static String makeVersion(final String version) {
        return "v" + version.replace(".", "_");
    }

}
