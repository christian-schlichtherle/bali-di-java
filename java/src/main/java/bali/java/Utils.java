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

import bali.Cache;
import bali.CachingStrategy;
import bali.Module;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bali.CachingStrategy.DISABLED;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

final class Utils {

    static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(Utils.class.getPackage().getName() + ".bundle");

    static final ModifierSet ABSTRACT = ModifierSet.of(Modifier.ABSTRACT);

    static final ModifierSet PROTECTED_PUBLIC = ModifierSet.of(Modifier.PROTECTED, Modifier.PUBLIC);

    static final ModifierSet PRIVATE_PROTECTED_PUBLIC = ModifierSet.of(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.PUBLIC);

    static final String CACHING_STRATEGY_CLASSNAME = CachingStrategy.class.getName();

    private static final String OBJECT_CLASSNAME = Object.class.getName();

    private static final String VOID_CLASSNAME = Void.class.getName();

    static CachingStrategy cachingStrategy(final Element e) {
        Optional<Cache> cache = getAnnotation(e, Cache.class);
        if (!cache.isPresent() && e.getModifiers().contains(Modifier.ABSTRACT)) {
            cache = Optional.ofNullable(e.getEnclosingElement()).flatMap(e2 -> getAnnotation(e2, Cache.class));
        }
        return cache.map(Cache::value).orElse(DISABLED);
    }

    static <A extends Annotation> Optional<A> getAnnotation(AnnotatedConstruct c, Class<A> k) {
        return Optional.ofNullable(c.getAnnotation(k));
    }

    static boolean hasAnnotation(AnnotatedConstruct c, Class<? extends Annotation> k) {
        return getAnnotation(c, k).isPresent();
    }

    static boolean hasParameters(ExecutableElement e) {
        return !e.getParameters().isEmpty() || !e.getTypeParameters().isEmpty();
    }

    static boolean hasDeclaredReturnType(ExecutableElement e) {
        return e.getReturnType().getKind() == DECLARED;
    }

    static boolean hasVoidReturnType(ExecutableElement e) {
        return isVoid(e.getReturnType());
    }

    static boolean isAbstract(Element e) {
        return e.getModifiers().contains(Modifier.ABSTRACT);
    }

    static boolean isCaching(Element e) {
        return getAnnotation(e, Cache.class).filter(c -> !DISABLED.equals(c.value())).isPresent();
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

    static boolean isModule(Element e) {
        return hasAnnotation(e, Module.class);
    }

    static boolean isStatic(Element e) {
        return e.getModifiers().contains(Modifier.STATIC);
    }

    static boolean isType(Element e) {
        return e instanceof TypeElement;
    }

    static boolean isObject(TypeMirror t) {
        return OBJECT_CLASSNAME.equals(t.toString());
    }

    static boolean isVoid(TypeMirror t) {
        return VOID.equals(t.getKind()) || VOID_CLASSNAME.equals(t.toString());
    }

    static ModifierSet modifiersOf(Element e) {
        return ModifierSet.of(e.getModifiers());
    }

    static String moduleClassName(TypeElement e) {
        return moduleInterfaceName(e) + "$";
    }

    static String moduleInterfaceName(TypeElement e) {
        return e.getQualifiedName() + "$";
    }

    static Name qualifiedNameOf(AnnotationMirror m) {
        return ((QualifiedNameable) m.getAnnotationType().asElement()).getQualifiedName();
    }

    private Utils() {
    }

    static String mkString(Collection<?> collection, String prefix, String delimiter, String suffix) {
        return mkString(collection.stream(), prefix, delimiter, suffix);
    }

    static String mkString(final Stream<?> stream, final String prefix, final String delimiter, final String suffix) {
        final String string = stream.map(Objects::toString).collect(Collectors.joining(delimiter));
        return string.isEmpty() ? "" : prefix + string + suffix;
    }
}
