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

import bali.*;
import bali.Module;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.val;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.time.OffsetDateTime;
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

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Accessors(fluent = true)
@SupportedAnnotationTypes("bali.*")
public class AnnotationProcessor extends AbstractProcessor {

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

    private int round;
    private List<Element> todo = new LinkedList<>();
    private boolean save;

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        // Suppress the following warning from the Java compiler, where X < Y:
        // Supported source version 'RELEASE_<X>' from annotation processor 'global.namespace.neuron.di.internal.<*>' less than -source '<Y>'
        return SourceVersion.latest();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        round++;

        val list = todo;
        todo = new LinkedList<>();
        list.forEach(this::processElement);

        annotations
                .stream()
                .filter(a -> moduleAnnotationName().equals(a.getQualifiedName()))
                .forEach(a -> roundEnv.getElementsAnnotatedWith(a).forEach(this::processElement));

        return true;
    }

    private void processElement(final Element e) {
        if (e instanceof TypeElement) {
            processTypeElement((TypeElement) e);
        }
    }

    private void processTypeElement(final TypeElement e) {
        if (e.getNestingKind().isNested()) {
            if (e.getModifiers().contains(Modifier.STATIC)) {
                if (!isExperimentalWarningSuppressed(e)) {
                    warn("Support for static nested classes is experimental.", e);
                }
            } else {
                error("Inner modules are not supported.", e);
                return;
            }
        }
        processCheckedTypeElement(e);
    }

    private void processCheckedTypeElement(final TypeElement e) {
        val out = new Output();
        save = true;
        new ModuleClass(e).accept(out); // may set save = false as side effect
        if (save) {
            try {
                val jfo = filer().createSourceFile(elements().getBinaryName(e) + "$", e);
                try (val w = jfo.openWriter()) {
                    w.write(out.toString());
                }
            } catch (IOException x) {
                error("Failed to process:\n" + x, e);
            }
        } else {
            todo.add(e);
        }
    }

    private Optional<TypeMirror> makeType(ExecutableElement e) {
        return e.getAnnotationMirrors()
                .stream()
                .filter(mirror -> makeAnnotationName().equals(qualifiedNameOf(mirror)))
                .findAny()
                .flatMap(mirror -> mirror
                        .getElementValues()
                        .values()
                        .stream()
                        .findFirst()
                        .map(AnnotationValue::getValue)
                        // If the make type is a module then defer its processing to the next round because it's not available yet.
                        .filter(v -> v instanceof TypeMirror || (save = false))
                        .map(TypeMirror.class::cast));
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

    private static Optional<CachingStrategy> cachingStrategy(final ExecutableElement e) {
        Optional<Cache> cache = getAnnotation(e, Cache.class);
        if (!cache.isPresent()) {
            cache = Optional.ofNullable(e.getEnclosingElement()).flatMap(e2 -> getAnnotation(e2, Cache.class));
        }
        return cache.map(Cache::value);
    }

    private Stream<ExecutableElement> filteredAbstractMethods(final TypeMirror t) {
        val element = typeElement(t);
        val list = allAbstractMethods(t)
                .filter(this::hasNotVoidReturnType)
                .collect(Collectors.toList());
        return allAbstractMethods(t)
                .filter(e1 -> list
                        .stream()
                        .filter(e2 -> e1 != e2)
                        .filter(e2 -> e1.getSimpleName().equals(e2.getSimpleName()))
                        .noneMatch(e2 -> {
                            val types = types();
                            val t1 = (ExecutableType) types.asMemberOf((DeclaredType) t, e1);
                            val t2 = (ExecutableType) types.asMemberOf((DeclaredType) t, e2);
                            return (types.isSubsignature(t1, t2) || types.isSubsignature(t2, t1))
                                    && !error("Cannot implement this " + (isInterface(element) ? "interface" : "class") + " ...", element)
                                    && !error("... because this method ...", e1)
                                    && !error("... clashes with this method => remove or override these methods.", e2);
                        }));
    }

    private Stream<ExecutableElement> allAbstractMethods(TypeMirror t) {
        return elements()
                .getAllMembers(typeElement(t))
                .stream()
                .filter(Utils::isAbstract)
                .filter(Utils::isMethod)
                .map(ExecutableElement.class::cast);
    }

    private TypeElement typeElement(TypeMirror t) {
        return (TypeElement) types().asElement(t);
    }

    private boolean error(final CharSequence message, final Element e) {
        messager().printMessage(ERROR, message, e);
        return false;
    }

    @SuppressWarnings("SameParameterValue")
    private void warn(CharSequence message, Element e) {
        messager().printMessage(WARNING, message, e);
    }

    private boolean hasNoParameters(ExecutableElement e) {
        return !hasParameters(e) || error("Dependency accessor methods cannot have parameters.", e);
    }

    private boolean hasNotPrimitiveReturnType(ExecutableElement e) {
        return !hasPrimitiveReturnType(e)
                || error("Cannot return primitive types from abstract methods in modules.", e);
    }

    private boolean hasNotVoidReturnType(ExecutableElement e) {
        return !hasVoidReturnType(e) ||
                error("Cannot return nothing from abstract methods in modules or dependencies.", e);
    }

    @RequiredArgsConstructor
    @Getter
    final class ModuleClass implements Consumer<Output> {

        private final TypeElement classElement;

        @Getter(lazy = true)
        private final ModifierSet classModifiers =
                modifiersOf(classElement()).retain(PRIVATE_PROTECTED_PUBLIC).add(ABSTRACT);

        @Getter(lazy = true)
        private final Name classSimpleName = classElement().getSimpleName();

        @Getter(lazy = true)
        private final DeclaredType classType = (DeclaredType) classElement().asType();

        String generated() {
            return String.format(Locale.ENGLISH,
                    "/*\n" +
                            "@javax.annotation.Generated(\n" +
                            "    comments = \"round=%d\",\n" +
                            "    date = \"%s\",\n" +
                            "    value = \"%s\"\n" +
                            ")\n" +
                            "*/",
                    round, OffsetDateTime.now(), AnnotationProcessor.class.getName());
        }

        @Getter(lazy = true)
        private final boolean isInterfaceType = isInterface(classElement());

        @Getter(lazy = true)
        private final PackageElement packageElement = elements().getPackageOf(classElement());

        @Getter(lazy = true)
        private final Name packageName = packageElement().getQualifiedName();

        @Getter(lazy = true)
        private final boolean hasAbstractMethods =
                allAbstractMethods(classType()).anyMatch(e ->
                        hasVoidReturnType(e)
                                || hasAnnotation(e, Lookup.class)
                                || hasPrimitiveReturnType(e)
                                || hasAnnotation(e, Make.class) && !makeType(e).isPresent()
                );

        Consumer<Output> forAllModuleMethods(ClassVisitor v) {
            return forAllModuleMethods0().andThen(out -> out.forAllProviderMethods(v));
        }

        Consumer<Output> forAllModuleMethods0() {
            return out -> filteredAbstractMethods(classType())
                    // HC SVNT DRACONES!
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    .filter(AnnotationProcessor.this::hasNotPrimitiveReturnType)
                    .filter(e -> !hasAnnotation(e, Make.class) || makeType(e).isPresent())
                    .map(e -> hasParameters(e)
                            ? newFactoryMethod(e).apply(new DisabledCachingVisitor())
                            : newProviderMethod(e).apply(methodVisitor(e)))
                    .forEach(c -> c.accept(out));
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
            private final boolean isMakeTypeAbstract = isAbstract(makeElement());

            @Getter(lazy = true)
            private final boolean isMakeTypeInterface = isInterface(makeElement());

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
                        .orElseGet(() -> (DeclaredType) methodReturnType());
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

            Consumer<Output> forAllAccessorMethods() {
                return out -> filteredAbstractMethods(makeType())
                        .filter(AnnotationProcessor.this::hasNoParameters)
                        .map(e -> {
                            val method = newAccessorMethod(e);
                            return method.apply(method.isCachingDisabled()
                                    ? new DisabledCachingVisitor()
                                    : methodVisitor(e));
                        })
                        .forEach(c -> c.accept(out));
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
                    if (isParameterRef()) {
                        return Optional.empty();
                    } else {
                        val element = resolveAccessedElement(classElement());
                        if (!element.isPresent()) {
                            error("Missing dependency: There is no parameter, method or field matching the name of this accessor method and the type of the module is not assignable to its return type.", methodElement());
                        }
                        return element;
                    }
                }

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement(Element e) {
                    for (; e instanceof TypeElement; e = e.getEnclosingElement()) {
                        val element = findAccessedElement((TypeElement) e);
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
                            .filter(e -> {
                                val name = e.getSimpleName();
                                return moduleMethodName().equals(name) || moduleFieldName().equals(name);
                            })
                            .collect(Collectors.toList());
                    // Prefer method access over field access:
                    val element = Stream
                            .<Function<Element, Boolean>>of(Utils::isMethod, Utils::isField)
                            .flatMap(f -> members.stream().filter(f::apply))
                            .map(e -> new Tuple2<TypeElement, Element>(where, e))
                            .findFirst();
                    return element.isPresent()
                            ? element
                            : types().isSubtype(classType(), methodReturnType())
                            ? Optional.of(new Tuple2<>(where, where))
                            : Optional.empty();
                }

                @Getter(lazy = true)
                private final String accessedElementRef = resolveAccessedElementRef();

                private String resolveAccessedElementRef() {
                    return isParameterRef()
                            ? moduleParamName().toString()
                            : accessedElement().map(Tuple2::t1).orElseGet(ModuleClass.this::classElement).getSimpleName()
                            + (isStaticRef() ? "$" : "$.this")
                            + (isModuleRef() ? "" : "." + (isMethodRef() ? moduleMethodName() + "()" : moduleFieldName() + ""));
                }

                @Getter(lazy = true)
                private final boolean isCachingDisabled =
                        isParameterRef() || isModuleRef() || accessedElement()
                                .map(Tuple2::t2)
                                .filter(Utils::isFinal)
                                .filter(Utils::isField)
                                .isPresent();

                @Getter(lazy = true)
                private final boolean isFieldRef =
                        accessedElement().map(Tuple2::t2).filter(Utils::isField).isPresent();

                @Override
                boolean resolveIsMethodRef() {
                    return accessedElement().map(Tuple2::t2).filter(Utils::isMethod).isPresent();
                }

                @Getter(lazy = true)
                private final boolean isParameterRef =
                        ModuleMethod
                                .this
                                .methodParameters()
                                .stream()
                                .anyMatch(variable -> moduleParamName().equals(variable.getSimpleName()));

                @Getter(lazy = true)
                private final boolean isStaticRef =
                        accessedElement().map(Tuple2::t2).filter(Utils::isStatic).isPresent();

                @Getter(lazy = true)
                private final boolean isModuleRef =
                        accessedElement().map(Tuple2::t2).filter(Utils::isType).isPresent();

                @Override
                ExecutableType resolveMethodType() {
                    return (ExecutableType) types().asMemberOf(makeType(), methodElement());
                }

                @Getter(lazy = true)
                private final Optional<Lookup> lookup = getAnnotation(methodElement(), Lookup.class);

                @Getter(lazy = true)
                private final Name moduleFieldName = resolveName(Lookup::field);

                @Getter(lazy = true)
                private final Name moduleMethodName = resolveName(Lookup::method);

                @Getter(lazy = true)
                private final Name moduleParamName = resolveName(Lookup::param);

                private Name resolveName(Function<Lookup, String> extractor) {
                    return lookup()
                            .flatMap(l -> Stream
                                    .of(extractor, Lookup::value)
                                    .map(f -> f.apply(l))
                                    .filter(s -> !s.isEmpty())
                                    .findFirst())
                            .map(elements()::getName)
                            .orElseGet(this::methodName);
                }

                @Override
                public Consumer<Output> apply(MethodVisitor v) {
                    return v.visitAccessorMethod(this,
                            ModuleMethod.this.methodParameters().isEmpty() ? "        " : "            ");
                }
            }
        }

        abstract class Method implements Function<MethodVisitor, Consumer<Output>> {

            @Getter(lazy = true)
            private final boolean isMethodRef = resolveIsMethodRef();

            boolean resolveIsMethodRef() {
                return false;
            }

            abstract ExecutableElement methodElement();

            @Getter(lazy = true)
            private final ModifierSet methodModifiers = modifiersOf(methodElement()).retain(PROTECTED_PUBLIC);

            @Getter(lazy = true)
            private final Name methodName = methodElement().getSimpleName();

            @Getter(lazy = true)
            private final List<? extends VariableElement> methodParameters =
                    unmodifiableList(methodElement().getParameters());

            @Getter(lazy = true)
            private final TypeMirror methodReturnType = methodType().getReturnType();

            @Getter(lazy = true)
            private final List<? extends TypeMirror> methodThrownTypes =
                    unmodifiableList(methodType().getThrownTypes());

            @Getter(lazy = true)
            private final ExecutableType methodType = resolveMethodType();

            ExecutableType resolveMethodType() {
                return (ExecutableType) types().asMemberOf(classType(), methodElement());
            }

            @Getter(lazy = true)
            private final List<? extends TypeParameterElement> methodTypeParameters =
                    unmodifiableList(methodElement().getTypeParameters());
        }
    }
}
