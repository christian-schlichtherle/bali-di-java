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

import bali.Lookup;
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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bali.CachingStrategy.DISABLED;
import static bali.java.Utils.*;
import static java.util.Collections.unmodifiableList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@SupportedAnnotationTypes("bali.*")
public final class AnnotationProcessor extends AbstractProcessor {

    @Getter(lazy = true)
    private final Elements elements = processingEnv.getElementUtils();

    @Getter(lazy = true)
    private final Filer filer = processingEnv.getFiler();

    @Getter(lazy = true)
    private final Name makeAnnotationName = getElements().getName(Make.class.getName());

    @Getter(lazy = true)
    private final Messager messager = processingEnv.getMessager();

    @Getter(lazy = true)
    private final Name moduleAnnotationName = getElements().getName(Module.class.getName());

    @Getter(lazy = true)
    private final Types types = processingEnv.getTypeUtils();

    private int round;
    private List<Name> todo = new LinkedList<>();
    private boolean save;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        // Suppress the following warning from the Java compiler, where X < Y:
        // Supported source version 'RELEASE_<X>' from annotation processor 'global.namespace.neuron.di.internal.<*>' less than -source '<Y>'
        return SourceVersion.latest();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        round++;

        val list = todo;
        todo = new LinkedList<>();
        list.stream().map(getElements()::getTypeElement).forEach(this::processElement);

        annotations
                .stream()
                .filter(a -> getModuleAnnotationName().equals(a.getQualifiedName()))
                .forEach(a -> roundEnv.getElementsAnnotatedWith(a).forEach(this::processElement));

        if (roundEnv.processingOver()) {
            todo
                    .stream()
                    .map(getElements()::getTypeElement)
                    .forEach(e -> error("Failed to generate code for this module type.", e));
        }

        return true;
    }

    private void processElement(final Element e) {
        if (e instanceof TypeElement) {
            processTypeElement((TypeElement) e);
        }
    }

    private void processTypeElement(final TypeElement e) {
        if (e.getNestingKind().isNested()) {
            val modifiers = ModifierSet.of(e.getModifiers());
            if (modifiers.retain(STATIC).isEmpty()) {
                error("Inner modules are not supported.", e);
                return;
            } else if (!modifiers.retain(PRIVATE_PROTECTED_PUBLIC).isEmpty()) {
                error("Static nested modules must be package-local.", e);
                return;
            }
        }
        processCheckedTypeElement(e);
    }

    private void processCheckedTypeElement(final TypeElement e) {
        val out = new Output();
        save = true;
        new ModuleType(e).apply(new TypeVisitor()).accept(out); // may set save = false as side effect
        if (save) {
            try {
                val jfo = getFiler().createSourceFile(getElements().getBinaryName(e) + "$", e);
                try (val w = jfo.openWriter()) {
                    w.write(out.toString());
                }
            } catch (IOException x) {
                error("Failed to process:\n" + x, e);
            }
        } else {
            todo.add(e.getQualifiedName());
        }
    }

    private Stream<ExecutableElement> filteredOverridableMethods(final TypeElement element) {
        val type = element.asType();
        val methods = allOverridableMethods(element)
                .filter(e -> !hasVoidReturnType(e))
                .collect(Collectors.toList());
        return methods
                .stream()
                .filter(e1 -> methods
                        .stream()
                        .filter(e2 -> e1 != e2)
                        .filter(e2 -> e1.getSimpleName().equals(e2.getSimpleName()))
                        .noneMatch(e2 -> {
                            val types = getTypes();
                            val t1 = (ExecutableType) types.asMemberOf((DeclaredType) type, e1);
                            val t2 = (ExecutableType) types.asMemberOf((DeclaredType) type, e2);
                            return (types.isSubsignature(t1, t2) || types.isSubsignature(t2, t1))
                                    && !error("Cannot implement this " + (isInterface(element) ? "interface" : "class") + " because ...", element)
                                    && !error("... this method clashes with ...", e1)
                                    && !error("... this method => remove or override these methods.", e2);
                        }));
    }

    private Stream<ExecutableElement> allOverridableMethods(final TypeElement element) {
        return getElements()
                .getAllMembers(element)
                .stream()
                .filter(Utils::isMethod)
                .map(ExecutableElement.class::cast)
                .filter(e -> isAbstract(e) || isCaching(e) && !hasParameters(e))
                .filter(e -> !isModule(element)
                        || checkMakeType(e) && checkReturnType(e) && checkParameterTypes(e)
                        || (save = false));
    }

    private boolean checkMakeType(ExecutableElement method) {
        return !hasAnnotation(method, Make.class) || makeType(method).filter(this::checkType).isPresent();
    }

    private boolean checkReturnType(ExecutableElement method) {
        return checkType(method.getReturnType());
    }

    private boolean checkType(final TypeMirror type) {
        if (TypeKind.ERROR.equals(type.getKind())) {
            return false;
        }
        val moduleElement = Optional.ofNullable(getTypes().asElement(type)).filter(Utils::isModule);
        if (moduleElement.isPresent()) {
            return moduleElement
                    .flatMap(m -> Optional.ofNullable(getElements().getTypeElement(moduleImplementationName((TypeElement) m))))
                    .isPresent();
        }
        return true;
    }

    private boolean checkParameterTypes(ExecutableElement e) {
        return e
                .getParameters()
                .stream()
                .map(Element::asType)
                .noneMatch(p -> TypeKind.ERROR.equals(p.getKind()));
    }

    private Optional<TypeMirror> makeType(ExecutableElement e) {
        return e.getAnnotationMirrors()
                .stream()
                .filter(mirror -> getMakeAnnotationName().equals(qualifiedNameOf(mirror)))
                .findAny()
                .flatMap(mirror -> mirror
                        .getElementValues()
                        .values()
                        .stream()
                        .findFirst()
                        .map(AnnotationValue::getValue)
                        .filter(v -> v instanceof TypeMirror)
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

    private TypeElement typeElement(TypeMirror t) {
        return (TypeElement) getTypes().asElement(t);
    }

    private boolean error(final CharSequence message, final Element e) {
        getMessager().printMessage(ERROR, message, e);
        return false;
    }

    private void warn(CharSequence message, Element e) {
        getMessager().printMessage(WARNING, message, e);
    }

    private boolean checkDeclaredReturnType(ExecutableElement e) {
        return hasDeclaredReturnType(e)
                || error("Provider or factory methods in modules must return class or interface types.", e);
    }

    private String typeParametersDecl(Parameterizable parameterizable) {
        return mkString(parameterizable.getTypeParameters().stream().map(p ->
                        p + mkString(p.getBounds().stream().filter(t -> !isObject(t)),
                                " extends ", " & ", "")),
                "<", ", ", "> ");
    }

    private boolean isSubtype(TypeMirror a, TypeMirror b, Element e) {
        return getTypes().isSubtype(a, b) || error(a + " is not a subtype of " + b + ".", e);
    }

    @RequiredArgsConstructor
    @Getter
    final class ModuleType implements Function<TypeVisitor, Consumer<Output>> {

        private final TypeElement typeElement;

        @Getter(lazy = true)
        private final ModifierSet typeModifiers =
                modifiersOf(getTypeElement()).retain(PRIVATE_PROTECTED_PUBLIC).add(ABSTRACT);

        @Getter(lazy = true)
        private final Name typeQualifiedName = getTypeElement().getQualifiedName();

        @Getter(lazy = true)
        private final Name typeSimpleName = getTypeElement().getSimpleName();

        @Getter(lazy = true)
        private final DeclaredType declaredType = (DeclaredType) getTypeElement().asType();

        @Getter(lazy = true)
        private final boolean interfaceType = isInterface(getTypeElement());

        @Getter(lazy = true)
        private final PackageElement packageElement = getElements().getPackageOf(getTypeElement());

        @Getter(lazy = true)
        private final Name packageName = getPackageElement().getQualifiedName();

        @Accessors(fluent = true)
        @Getter(lazy = true)
        private final boolean hasAbstractMethods =
                allOverridableMethods(getTypeElement())
                        .filter(Utils::isAbstract)
                        .anyMatch(e ->
                                hasVoidReturnType(e) || hasAnnotation(e, Lookup.class) || !hasDeclaredReturnType(e));

        @Getter(lazy = true)
        private final String typeParametersDecl = typeParametersDecl(getTypeElement());

        Consumer<Output> forAllModuleMethods() {
            return out -> filteredOverridableMethods(getTypeElement())
                    // HC SVNT DRACONES!
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    .filter(AnnotationProcessor.this::checkDeclaredReturnType)
                    .map(e -> newModuleMethod(e).apply(hasParameters(e)
                            ? new DisabledCachingVisitor()
                            : methodVisitor(e)))
                    .forEach(c -> c.accept(out));
        }

        ModuleMethod newModuleMethod(ExecutableElement e) {
            return new ModuleMethod() {

                @Override
                ExecutableElement methodElement() {
                    return e;
                }
            };
        }

        String generated() {
            return String.format(Locale.ENGLISH,
                    "@javax.annotation.Generated(\n" +
                            "    comments = \"round=%d, version=%s\",\n" +
                            "    date = \"%s\",\n" +
                            "    value = \"%s\"\n" +
                            ")",
                    round, RESOURCE_BUNDLE.getString("version"), OffsetDateTime.now(),
                    AnnotationProcessor.class.getName());
        }

        @Override
        public Consumer<Output> apply(TypeVisitor v) {
            return v.visitModuleType(this);
        }

        abstract class ModuleMethod extends Method {

            @Getter(lazy = true)
            private final String superElementRef = (isInterfaceType() ? getTypeQualifiedName() + "." : "") + "super";

            @Override
            boolean resolveNonNull() {
                return isAbstract(methodElement());
            }

            @Getter(lazy = true)
            private final boolean abstractMakeType = isAbstract(getMakeElement());

            @Getter(lazy = true)
            private final boolean interfaceMakeType = isInterface(getMakeElement());

            @Getter(lazy = true)
            private final TypeElement makeElement = typeElement(getMakeType());

            @Getter(lazy = true)
            private final Name makeQualifiedName = getMakeElement().getQualifiedName();

            @Getter(lazy = true)
            private final Name makeSimpleName = getMakeElement().getSimpleName();

            @Getter(lazy = true)
            private final DeclaredType makeType = resolveMakeType();

            private DeclaredType resolveMakeType() {
                val declaredMakeType = AnnotationProcessor
                        .this
                        .makeType(methodElement())
                        .flatMap(this::parameterizedReturnType)
                        .filter(t -> isSubtype(t, getMethodReturnType(), methodElement()))
                        .map(TypeMirror.class::cast);
                final TypeMirror declaredReturnType = declaredMakeType.orElseGet(this::getMethodReturnType);
                val declaredReturnElement = typeElement(declaredReturnType);
                if (isModule(declaredReturnElement)) {
                    val moduleType = Optional
                            .ofNullable(getElements().getTypeElement(moduleImplementationName(declaredReturnElement)))
                            .map(Element::asType)
                            .flatMap(this::parameterizedReturnType);
                    if (moduleType.isPresent()) {
                        return moduleType.get();
                    }
                }
                return (DeclaredType) declaredReturnType;
            }

            private Optional<DeclaredType> parameterizedReturnType(final TypeMirror makeType) {
                val makeElement = typeElement(makeType);
                final List<? extends TypeMirror> methodReturnTypeArguments = getMethodReturnType() instanceof DeclaredType
                        ? ((DeclaredType) getMethodReturnType()).getTypeArguments()
                        : Collections.emptyList();
                val partitionedMethodReturnTypeArguments =
                        methodReturnTypeArguments
                                .stream()
                                .collect(Collectors.partitioningBy(t -> t.getKind() == TypeKind.TYPEVAR,
                                        Collectors.<TypeMirror, LinkedList<TypeMirror>>toCollection(LinkedList::new)));
                val variableMethodReturnTypeArguments =
                        partitionedMethodReturnTypeArguments
                                .get(true)
                                .stream()
                                .collect(Collectors.toMap(t -> getTypes().asElement(t).getSimpleName(), t -> t));
                val nonVariableMethodReturnTypeArguments =
                        partitionedMethodReturnTypeArguments
                                .get(false);
                val makeTypeArgs = makeElement
                        .getTypeParameters()
                        .stream()
                        .map(Element::getSimpleName)
                        .flatMap(n -> Stream
                                .<Supplier<TypeMirror>>of(
                                        () -> variableMethodReturnTypeArguments.get(n),
                                        nonVariableMethodReturnTypeArguments::poll
                                )
                                .map(Supplier::get)
                                .filter(Objects::nonNull)
                                .limit(1))
                        .collect(Collectors.toList());
                try {
                    return Optional.of(getTypes().getDeclaredType(makeElement, makeTypeArgs.toArray(new TypeMirror[0])));
                } catch (final IllegalArgumentException ignored) {
                    error("Incompatible type parameters.", methodElement());
                    return Optional.empty();
                }
            }

            Consumer<Output> forAllAccessorMethods() {
                return out -> filteredOverridableMethods(getMakeElement())
                        .filter(e -> !hasParameters(e))
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

            @Override
            public Consumer<Output> apply(MethodVisitor v) {
                return v.visitModuleMethod(this);
            }

            abstract class AccessorMethod extends Method {

                @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                @Getter(lazy = true)
                private final Optional<Tuple2<TypeElement, Element>> accessedElement = resolveAccessedElement();

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement() {
                    if (isParameterRef() || isSuperRef()) {
                        return Optional.empty();
                    } else {
                        val element = resolveAccessedElement(getTypeElement());
                        if (!element.isPresent()) {
                            warn("This module " + (isInterfaceType() ? "interface" : "class") + " is missing the dependency returned by ...", getTypeElement());
                            warn("... this accessor method.", methodElement());
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
                    val members = getElements()
                            .getAllMembers(where)
                            .stream()
                            .filter(e -> {
                                val name = e.getSimpleName();
                                return getModuleMethodName().equals(name) || getModuleFieldName().equals(name);
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
                            : getTypes().isSubtype(getDeclaredType(), getMethodReturnType())
                            ? Optional.of(new Tuple2<>(where, where))
                            : Optional.empty();
                }

                @Getter(lazy = true)
                private final String accessedElementRef = resolveAccessedElementRef();

                private String resolveAccessedElementRef() {
                    return isParameterRef()
                            ? getModuleParamName().toString()
                            : isSuperRef()
                            ? (isInterfaceMakeType() ? getMakeQualifiedName() + "." : "") + "super." + getMethodName() + "()"
                            : getAccessedElement().map(Tuple2::getT1).orElseGet(ModuleType.this::getTypeElement).getSimpleName()
                            + (isStaticRef() ? "$" : "$.this")
                            + (isModuleRef() ? "" : "." + (isFieldRef() ? getModuleFieldName() + "" : getModuleMethodName() + "()"));
                }

                @Getter(lazy = true)
                private final boolean cachingDisabled = resolveCachingDisabled();

                private boolean resolveCachingDisabled() {
                    val element = getAccessedElement().map(Tuple2::getT2);
                    return isParameterRef()
                            || isModuleRef()
                            || element.filter(Utils::isFinal).filter(Utils::isField).isPresent()
                            || element
                            .flatMap(Utils::cachingStrategy)
                            .flatMap(s1 -> cachingStrategy(methodElement()).filter(s1::equals))
                            .isPresent();
                }

                @Getter(lazy = true)
                private final boolean fieldRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isField).isPresent();

                @Override
                boolean resolveMethodRef() {
                    return getAccessedElement().map(Tuple2::getT2).filter(Utils::isMethod).isPresent();
                }

                @Getter(lazy = true)
                private final boolean parameterRef =
                        ModuleMethod
                                .this
                                .getMethodParameters()
                                .stream()
                                .anyMatch(variable -> getModuleParamName().equals(variable.getSimpleName()));

                @Getter(lazy = true)
                private final boolean staticRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isStatic).isPresent();

                @Getter(lazy = true)
                private final boolean moduleRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isType).isPresent();

                @Override
                boolean resolveNonNull() {
                    return getAccessedElement().map(Tuple2::getT2).filter(Utils::isAbstract).isPresent();
                }

                @Override
                ExecutableType resolveMethodType() {
                    return (ExecutableType) getTypes().asMemberOf(getMakeType(), methodElement());
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
                    return getLookup()
                            .flatMap(l -> Stream
                                    .of(extractor, Lookup::value)
                                    .map(f -> f.apply(l))
                                    .filter(s -> !s.isEmpty())
                                    .findFirst())
                            .map(getElements()::getName)
                            .orElseGet(this::getMethodName);
                }

                @Override
                public Consumer<Output> apply(MethodVisitor v) {
                    return v.visitAccessorMethod(this);
                }
            }
        }

        abstract class Method implements Function<MethodVisitor, Consumer<Output>> {

            @Getter(lazy = true)
            private final boolean methodRef = resolveMethodRef();

            boolean resolveMethodRef() {
                return false;
            }

            @Getter(lazy = true)
            private final boolean nonNull = resolveNonNull();

            abstract boolean resolveNonNull();

            @Getter(lazy = true)
            private final boolean superRef = !isAbstract(methodElement());

            abstract ExecutableElement methodElement();

            @Getter(lazy = true)
            private final ModifierSet methodModifiers = modifiersOf(methodElement()).retain(PROTECTED_PUBLIC);

            @Getter(lazy = true)
            private final Name methodName = methodElement().getSimpleName();

            @Getter(lazy = true)
            private final List<? extends VariableElement> methodParameters =
                    unmodifiableList(methodElement().getParameters());

            @Getter(lazy = true)
            private final TypeMirror methodReturnType = getMethodType().getReturnType();

            @Getter(lazy = true)
            private final ExecutableType methodType = resolveMethodType();

            ExecutableType resolveMethodType() {
                return (ExecutableType) getTypes().asMemberOf(getDeclaredType(), methodElement());
            }

            @Getter(lazy = true)
            private final String methodTypeParametersDecl = typeParametersDecl(methodElement());

            @Getter(lazy = true)
            private final String methodParametersDecl =
                    mkString(getMethodParameters().stream().map(var -> var.asType() + " " + var),
                            "", ", ", "");

            @Getter(lazy = true)
            private final String methodThrownTypesDecl =
                    mkString(getMethodType().getThrownTypes(), "throws ", ", ", " ");
        }
    }
}
