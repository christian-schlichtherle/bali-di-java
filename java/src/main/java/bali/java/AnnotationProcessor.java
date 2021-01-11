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
import bali.Make;
import bali.Module;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.val;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bali.CachingStrategy.DISABLED;
import static bali.java.Utils.*;
import static java.util.Collections.unmodifiableList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

@Accessors(fluent = true)
@SupportedAnnotationTypes("bali.*")
public final class AnnotationProcessor extends AbstractProcessor {

    @Getter(lazy = true)
    private final Elements elements = processingEnv.getElementUtils();

    @Getter(lazy = true)
    private final Filer filer = processingEnv.getFiler();

    @Getter(lazy = true)
    private final Name makeAnnotationName = elements().getName(Make.class.getName());

    @Getter(lazy = true)
    private final Messager messager = processingEnv.getMessager();

    @Getter(lazy = true)
    private final Name moduleAnnotationName = elements().getName(Module.class.getName());

    @Getter(lazy = true)
    private final Types types = processingEnv.getTypeUtils();

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        // Suppress the following warning from the Java compiler, where X < Y:
        // Supported source version 'RELEASE_<X>' from annotation processor 'global.namespace.neuron.di.internal.<*>' less than -source '<Y>'
        return SourceVersion.latest();
    }

    @Override
    public final boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        annotations
                .stream()
                .filter(a -> moduleAnnotationName().equals(a.getQualifiedName()))
                .forEach(a -> roundEnv.getElementsAnnotatedWith(a).forEach(this::process));
        return true;
    }

    void process(final Element e) {
        if (e instanceof TypeElement) {
            process((TypeElement) e);
        }
    }

    private void process(final TypeElement e) {
        if (e.getNestingKind().isNested()) {
            if (e.getModifiers().contains(Modifier.STATIC)) {
                if (!Optional
                        .ofNullable(e.getAnnotation(SuppressWarnings.class))
                        .map(SuppressWarnings::value)
                        .map(Arrays::asList)
                        .filter(l -> l.contains("experimental"))
                        .isPresent()) {
                    warn("Support for static nested classes is experimental.", e);
                }
            } else {
                error("Inner modules are not supported.", e);
                return;
            }
        }

        val out = new Output();
        new ModuleClass(e).accept(out);
        val sourceCode = out.toString();
        try (val w = filer().createSourceFile(elements().getBinaryName(e) + "$", e).openWriter()) {
            w.write(sourceCode);
        } catch (IOException x) {
            error("Failed to process:\n" + x, e);
        }
    }

    private MethodVisitor methodVisitor(final ExecutableElement e) {
        switch (cachingStrategy(e).orElse(DISABLED)) {
            case DISABLED:
                return new DisabledCachingVisitor();
            case NOT_THREAD_SAFE:
                return new NotThreadSafeCachingVisitor();
            case THREAD_SAFE:
                return new ThreadSafeCachingVisitor();
            case THREAD_LOCAL:
                return new ThreadLocalCachingVisitor();
            default:
                error("Unknown caching strategy - caching is disabled.", e);
                return new DisabledCachingVisitor();
        }
    }

    private static Optional<CachingStrategy> cachingStrategy(final ExecutableElement element) {
        Optional<Cache> cache = Optional.ofNullable(element.getAnnotation(Cache.class));
        if (!cache.isPresent()) {
            cache = Optional.ofNullable(element.getEnclosingElement()).flatMap(e -> Optional.ofNullable(e.getAnnotation(Cache.class)));
        }
        return cache.map(Cache::value);
    }

    private Optional<TypeMirror> makeType(Element element) {
        return element
                .getAnnotationMirrors()
                .stream()
                .filter(mirror -> makeAnnotationName().equals(qualifiedNameOf(mirror)))
                .findAny()
                .flatMap(mirror -> mirror
                        .getElementValues()
                        .values()
                        .stream()
                        .findFirst()
                        .map(v -> (TypeMirror) v.getValue()));
    }

    private TypeElement typeElement(TypeMirror type) {
        return (TypeElement) types().asElement(type);
    }

    private Stream<ExecutableElement> forAllInjectableMethods(final TypeMirror type) {
        val element = typeElement(type);
        val list = elements()
                .getAllMembers(element)
                .stream()
                .filter(Utils::isAbstract)
                .filter(Utils::isMethod)
                .map(ExecutableElement.class::cast)
                .filter(this::isNotVoidReturnType)
                .collect(Collectors.toList());
        return list
                .stream()
                .filter(e1 -> list
                        .stream()
                        .filter(e2 -> e1 != e2)
                        .filter(e2 -> e1.getSimpleName().equals(e2.getSimpleName()))
                        .noneMatch(e2 -> {
                            val types = types();
                            val t1 = (ExecutableType) types.asMemberOf((DeclaredType) type, e1);
                            val t2 = (ExecutableType) types.asMemberOf((DeclaredType) type, e2);
                            return (types.isSubsignature(t1, t2) || types.isSubsignature(t2, t1))
                                    && !error("Cannot implement this " + (isInterface(element) ? "interface" : "class") + " ...", element)
                                    && !error("... because this method ...", e1)
                                    && !error("... clashes with this method => remove or override these methods.", e2);
                        }));
    }

    private boolean isNotVoidReturnType(ExecutableElement e) {
        return !isVoid(e.getReturnType())
                || error("Cannot implement this method because it returns nothing.", e);
    }

    private boolean error(final CharSequence message, final Element e) {
        messager().printMessage(ERROR, message, e);
        return false;
    }

    @SuppressWarnings("SameParameterValue")
    private void warn(CharSequence message, Element e) {
        messager().printMessage(WARNING, message, e);
    }

    @RequiredArgsConstructor
    @Getter
    final class ModuleClass implements Consumer<Output> {

        @Getter(lazy = true)
        private final PackageElement packageElement = elements().getPackageOf(classElement());

        @Getter(lazy = true)
        private final Name packageName = packageElement().getQualifiedName();

        private final TypeElement classElement;

        @Getter(lazy = true)
        private final DeclaredType classType = (DeclaredType) classElement().asType();

        @Getter(lazy = true)
        private final Name classSimpleName = classElement().getSimpleName();

        @Getter(lazy = true)
        private final boolean isInterfaceType = isInterface(classElement());

        Consumer<Output> forAllModuleMethods(ClassVisitor v) {
            return forAllModuleMethods0().andThen(out -> out.forAllProviderMethods(v));
        }

        Consumer<Output> forAllModuleMethods0() {
            return out -> forAllInjectableMethods(classType())
                    .filter(this::isNotPrimitiveReturnType)
                    .map(e -> hasParameters(e)
                            ? newFactoryMethod(e).apply(new DisabledCachingVisitor())
                            : newProviderMethod(e).apply(methodVisitor(e)))
                    .forEach(c -> c.accept(out));
        }

        private boolean isNotPrimitiveReturnType(ExecutableElement e) {
            return !isPrimitive(e.getReturnType())
                    || error("Cannot implement this method because it has a primitive return type.", e);
        }

        @Override
        public void accept(Output out) {
            new ClassVisitor().visitModuleClass(this).accept(out);
        }

        FactoryMethod newFactoryMethod(ExecutableElement e) {
            return new FactoryMethod() {

                @Override
                ExecutableElement methodElement() {
                    return e;
                }
            };
        }

        abstract class FactoryMethod extends ModuleMethod {

            @Override
            public Consumer<Output> apply(MethodVisitor v) {
                return v.visitFactoryMethod(this);
            }
        }

        ProviderMethod newProviderMethod(ExecutableElement e) {
            return new ProviderMethod() {

                @Override
                ExecutableElement methodElement() {
                    return e;
                }
            };
        }

        abstract class ProviderMethod extends ModuleMethod {

            @Getter(lazy = true)
            private final Name makeSimpleName = makeElement().getSimpleName();

            @Override
            public Consumer<Output> apply(MethodVisitor v) {
                return v.visitProviderMethod(this);
            }
        }

        abstract class ModuleMethod extends Method {

            @Getter(lazy = true)
            private final TypeElement makeElement = typeElement(makeType());

            @Getter(lazy = true)
            private final DeclaredType makeType = resolveMakeType();

            private DeclaredType resolveMakeType() {
                return AnnotationProcessor
                        .this
                        .makeType(methodElement())
                        .flatMap(this::deriveMakeType)
                        .filter(t -> isSubTypeOf(t, methodReturnType(), methodElement()))
                        .orElse((DeclaredType) methodReturnType());
            }

            private Optional<DeclaredType> deriveMakeType(final TypeMirror makeType) {
                val makeElement = typeElement(makeType);
                val methodTypeVariables = methodType()
                        .getTypeVariables()
                        .stream()
                        .collect(Collectors.toMap(v -> v.asElement().getSimpleName(), v -> v));
                final List<? extends TypeMirror> methodReturnTypeArguments = methodReturnType() instanceof DeclaredType
                        ? ((DeclaredType) methodReturnType()).getTypeArguments()
                        : Collections.emptyList();
                val nonVariableMethodReturnTypeArguments = methodReturnTypeArguments
                        .stream()
                        .filter(t -> t.getKind() != TypeKind.TYPEVAR)
                        .collect(Collectors.toCollection(LinkedList::new));
                val makeTypeParameters = makeElement.getTypeParameters();
                val makeTypeArgs = makeTypeParameters
                        .stream()
                        .map(Element::getSimpleName)
                        .flatMap(n -> Utils
                                .streamOfNonNull(
                                        () -> methodTypeVariables.get(n),
                                        nonVariableMethodReturnTypeArguments::poll)
                                .limit(1))
                        .collect(Collectors.toList());
                try {
                    return Optional.of(types().getDeclaredType(makeElement, makeTypeArgs.toArray(new TypeMirror[0])));
                } catch (final IllegalArgumentException ignored) {
                    error("Incompatible type parameters.", methodElement());
                    return Optional.empty();
                }
            }

            private boolean isSubTypeOf(TypeMirror a, TypeMirror b, ExecutableElement e) {
                return types().isSubtype(a, b) || error(a + " is not a subtype of " + b + ".", e);
            }

            @Getter(lazy = true)
            private final boolean isMakeTypeAbstract = isAbstract(makeElement());

            @Getter(lazy = true)
            private final boolean isMakeTypeInterface = isInterface(makeElement());

            Consumer<Output> forAllAccessorMethods() {
                return out -> forAllInjectableMethods(makeType())
                        .filter(this::hasNoParameters)
                        .map(e -> {
                            val method = newAccessorMethod(e);
                            return method.apply(method.isCachingDisabled()
                                    ? new DisabledCachingVisitor()
                                    : methodVisitor(e));
                        })
                        .forEach(c -> c.accept(out));
            }

            private boolean hasNoParameters(ExecutableElement e) {
                return !hasParameters(e)
                        || error("Cannot implement accessor methods with parameters.", e);
            }

            AccessorMethod newAccessorMethod(ExecutableElement e) {
                return new AccessorMethod() {

                    @Override
                    ExecutableElement methodElement() {
                        return e;
                    }
                };
            }

            abstract class AccessorMethod extends Method {

                @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                @Getter(lazy = true)
                private final Optional<Tuple2<TypeElement, Element>> accessedElement = resolveAccessedElement();

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement() {
                    val element = resolveAccessedElement(classElement());
                    if (!element.isPresent()) {
                        error("Missing dependency: There is no method or field named like this accessor method and it cannot return the module instance.", methodElement());
                    }
                    return element;
                }

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement(final TypeElement start) {
                    for (Element next = start; next instanceof TypeElement; next = next.getEnclosingElement()) {
                        val element = findAccessedElement((TypeElement) next);
                        if (element.isPresent()) {
                            return element;
                        }
                    }
                    return Optional.empty();
                }

                private Optional<Tuple2<TypeElement, Element>> findAccessedElement(final TypeElement where) {
                    val members = elements()
                            .getAllMembers(where)
                            .stream()
                            .filter(e -> methodName().equals(e.getSimpleName()))
                            .map(e -> new Tuple2<TypeElement, Element>(where, e))
                            .collect(Collectors.toList());
                    // Prefer method access over field access:
                    val element = Stream
                            .<Function<Element, Boolean>>of(Utils::isMethod, Utils::isField)
                            .flatMap(f -> members.stream().filter(t -> f.apply(t.t2())))
                            .findFirst();
                    return element.isPresent()
                            ? element
                            : types().isSubtype(classType(), methodReturnType())
                            ? Optional.of(new Tuple2<>(where, where))
                            : Optional.empty();
                }

                @Getter(lazy = true)
                private final boolean isCachingDisabled =
                        isParameterAccess() || accessedElement()
                                .map(Tuple2::t2)
                                .filter(Utils::isFinal)
                                .filter(Utils::isField)
                                .isPresent();

                @Getter(lazy = true)
                private final boolean isFieldAccess =
                        accessedElement().map(Tuple2::t2).filter(Utils::isField).isPresent();

                @Getter(lazy = true)
                private final boolean isParameterAccess =
                        ModuleMethod
                                .this
                                .methodParameters()
                                .stream()
                                .anyMatch(variable -> methodName().equals(variable.getSimpleName()));

                @Getter(lazy = true)
                private final boolean isStaticAccess =
                        accessedElement().map(Tuple2::t2).filter(Utils::isStatic).isPresent();

                @Getter(lazy = true)
                private final boolean isTypeAccess =
                        accessedElement().map(Tuple2::t2).filter(Utils::isType).isPresent();

                @Override
                boolean resolveIsMethodAccess() {
                    return accessedElement().map(Tuple2::t2).filter(Utils::isMethod).isPresent();
                }

                @Override
                ExecutableType resolveMethodType() {
                    return (ExecutableType) types().asMemberOf(makeType(), methodElement());
                }

                @Getter(lazy = true)
                private final String accessedElementRef = resolveAccessedElementRef();

                private String resolveAccessedElementRef() {
                    if (isParameterAccess()) {
                        return methodName().toString();
                    } else {
                        return accessedElement()
                                .map(Tuple2::t1)
                                .map(Element::getSimpleName)
                                .orElseGet(ModuleClass.this::classSimpleName)
                                + (isStaticAccess() ? "$" : "$.this")
                                + (isTypeAccess() ? "" : "." + methodName() + (isMethodAccess() ? "()" : ""));
                    }
                }

                @Override
                public Consumer<Output> apply(MethodVisitor v) {
                    return v.visitAccessorMethod(this,
                            ModuleMethod.this.methodParameters().isEmpty() ? "        " : "            ");
                }
            }
        }

        abstract class Method implements Function<MethodVisitor, Consumer<Output>> {

            abstract ExecutableElement methodElement();

            @Getter(lazy = true)
            private final ExecutableType methodType = resolveMethodType();

            ExecutableType resolveMethodType() {
                return (ExecutableType) types().asMemberOf(classType(), methodElement());
            }

            @Getter(lazy = true)
            private final ModifierSet methodModifiers = modifiersOf(methodElement()).retain(PROTECTED_PUBLIC);

            @Getter(lazy = true)
            private final List<? extends TypeParameterElement> methodTypeParameters =
                    unmodifiableList(methodElement().getTypeParameters());

            @Getter(lazy = true)
            private final TypeMirror methodReturnType = methodType().getReturnType();

            @Getter(lazy = true)
            private final Name methodName = methodElement().getSimpleName();

            @Getter(lazy = true)
            private final List<? extends VariableElement> methodParameters =
                    unmodifiableList(methodElement().getParameters());

            @Getter(lazy = true)
            private final List<? extends TypeMirror> methodThrownTypes =
                    unmodifiableList(methodType().getThrownTypes());

            @Getter(lazy = true)
            private final boolean isMethodAccess = resolveIsMethodAccess();

            boolean resolveIsMethodAccess() {
                return false;
            }
        }
    }
}
