/*
 * Copyright © 2021 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bali.java;

import lombok.val;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static javax.lang.model.type.TypeKind.VOID;

final class Utils {

    static final ModifierSet ABSTRACT = ModifierSet.of(Modifier.ABSTRACT);

    static final ModifierSet PROTECTED_PUBLIC = ModifierSet.of(Modifier.PROTECTED, Modifier.PUBLIC);

    static final ModifierSet PRIVATE_PROTECTED_PUBLIC = ModifierSet.of(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.PUBLIC);

    private static final String VOID_CLASSNAME = Void.class.getName();

    static <A extends Annotation> Optional<A> getAnnotation(AnnotatedConstruct c, Class<A> k) {
        return Optional.ofNullable(c.getAnnotation(k));
    }

    static boolean hasAnnotation(AnnotatedConstruct c, Class<? extends Annotation> k) {
        return getAnnotation(c, k).isPresent();
    }

    static boolean hasParameters(ExecutableElement e) {
        return !e.getParameters().isEmpty() || !e.getTypeParameters().isEmpty();
    }

    static boolean hasPrimitiveReturnType(ExecutableElement e) {
        return isPrimitive(e.getReturnType());
    }

    static boolean hasVoidReturnType(ExecutableElement e) {
        return isVoid(e.getReturnType());
    }

    static boolean isAbstract(Element e) {
        return e.getModifiers().contains(Modifier.ABSTRACT);
    }

    static boolean isExperimentalWarningSuppressed(Element e) {
        for (; e != null; e = e.getEnclosingElement()) {
            val w = getAnnotation(e, SuppressWarnings.class);
            if (w.map(SuppressWarnings::value)
                    .map(Arrays::asList)
                    .filter(l -> l.contains("experimental"))
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    static boolean isField(Element e) {
        return e.getKind().isField();
    }

    static boolean isFinal(Element e) {
        return e.getModifiers().contains(Modifier.FINAL);
    }

    static boolean isInterface(Element e) {
        return e.getKind().isInterface();
    }

    static boolean isMethod(Element e) {
        return ElementKind.METHOD.equals(e.getKind());
    }

    static boolean isPrimitive(TypeMirror e) {
        return e.getKind().isPrimitive();
    }

    static boolean isStatic(Element e) {
        return e.getModifiers().contains(Modifier.STATIC);
    }

    static boolean isType(Element e) {
        return e instanceof TypeElement;
    }

    static boolean isVoid(TypeMirror t) {
        return VOID.equals(t.getKind()) || VOID_CLASSNAME.equals(t.toString());
    }

    static ModifierSet modifiersOf(Element e) {
        return ModifierSet.of(e.getModifiers());
    }

    static Name qualifiedNameOf(AnnotationMirror m) {
        return ((QualifiedNameable) m.getAnnotationType().asElement()).getQualifiedName();
    }

    @SafeVarargs
    static <T> Stream<T> streamOfNonNull(Supplier<? extends T>... suppliers) {
        return Stream
                .of(suppliers)
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .map(t -> (T) t);
    }

    private Utils() {
    }
}
