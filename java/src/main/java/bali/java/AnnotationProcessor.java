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

    private void deferProcessing(TypeElement e) {
        todo.add(e.getQualifiedName());
    }

    private void processElement(final Element e) {
        if (e instanceof TypeElement) {
            processTypeElement((TypeElement) e);
        }
    }

    private void processTypeElement(final TypeElement e) {
        if (!isInterface(e)) {
            error("A module must be an interface.", e);
            return;
        } else if (e.getNestingKind().isNested()) {
            val modifiers = ModifierSet.of(e.getModifiers());
            if (!modifiers.retain(PRIVATE_PROTECTED_PUBLIC).isEmpty()) {
                error("A static nested module must be package-local.", e);
                return;
            }
        }
        if (e.getInterfaces().stream().allMatch(this::checkType)) {
            processCheckedTypeElement(e);
        } else {
            deferProcessing(e);
        }
    }

    private void processCheckedTypeElement(final TypeElement e) {
        val typeVisitor = new TypeVisitor();
        val moduleType = new ModuleType(e);
        val iface = new Output();
        val klass = new Output();

        save = true;
        typeVisitor.visitModuleInterface(moduleType).accept(iface); // may set save = false as side effect
        if (save) {
            typeVisitor.visitModuleClass(moduleType).accept(klass); // dito
        }
        if (save) {
            val baseName = getElements().getBinaryName(e);
            try {
                try (val w = getFiler().createSourceFile(baseName + "$", e).openWriter()) {
                    w.write(iface.toString());
                }
                try (val w = getFiler().createSourceFile(baseName + "$$", e).openWriter()) {
                    w.write(klass.toString());
                }
            } catch (IOException x) {
                error("Failed to process:\n" + x, e);
            }
        } else {
            deferProcessing(e);
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
                                    && !error("Cannot implement this interface because ...", element)
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
        } else {
            val moduleElement = Optional.ofNullable(getTypes().asElement(type)).filter(Utils::isModule);
            if (moduleElement.isPresent()) {
                return moduleElement
                        .flatMap(m -> Optional.ofNullable(getElements().getTypeElement(moduleClassName((TypeElement) m))))
                        .isPresent();
            } else {
                return true;
            }
        }
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
        switch (cachingStrategy(e)) {
            case DISABLED:
                return new DisabledCachingInClassVisitor();
            case NOT_THREAD_SAFE:
                return new NotThreadSafeCachingVisitor();
            case THREAD_SAFE:
                return new ThreadSafeCachingVisitor();
            case THREAD_LOCAL:
                return new ThreadLocalCachingVisitor();
            default:
                error("Unknown caching strategy - caching is disabled.", e);
                return new DisabledCachingInClassVisitor();
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
        return typeParametersWithBoundsDecl(parameterizable, Function.identity());
    }

    private String typeParametersWithBoundsDecl(Parameterizable parameterizable) {
        return typeParametersWithBoundsDecl(parameterizable,
                p -> p + mkString(p.getBounds().stream().filter(t -> !isObject(t)), " extends ", " & ", ""));
    }

    private String typeParametersWithBoundsDecl(Parameterizable parameterizable, Function<? super TypeParameterElement, ?> f) {
        return mkString(parameterizable.getTypeParameters().stream().map(f), "<", ", ", "> ");
    }

    private boolean isSubtype(TypeMirror a, TypeMirror b, Element e) {
        return getTypes().isSubtype(a, b) || error(a + " is not a subtype of " + b + ".", e);
    }

    @RequiredArgsConstructor
    @Getter
    final class ModuleType {

        private final TypeElement typeElement;

        @Getter(lazy = true)
        private final ModifierSet typeModifiers = modifiersOf(getTypeElement()).retain(PRIVATE_PROTECTED_PUBLIC);

        @Getter(lazy = true)
        private final Name typeQualifiedName = getTypeElement().getQualifiedName();

        @Getter(lazy = true)
        private final Name typeSimpleName = getTypeElement().getSimpleName();

        @Getter(lazy = true)
        private final DeclaredType declaredType = (DeclaredType) getTypeElement().asType();

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

        @Getter(lazy = true)
        private final String typeParametersWithBoundsDecl = typeParametersWithBoundsDecl(getTypeElement());

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

        Consumer<Output> forAllModuleMethodsInInterface() {
            return out -> filteredOverridableMethods(getTypeElement())
                    .filter(Utils::isAbstract)
                    // HC SVNT DRACONES!
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    .filter(AnnotationProcessor.this::checkDeclaredReturnType)
                    .map(e -> new DisabledCachingInInterfaceVisitor().visitModuleMethodInInterface(newModuleMethod(e)))
                    .forEach(c -> c.accept(out));
        }

        Consumer<Output> forAllModuleMethodsInClass() {
            return out -> filteredOverridableMethods(getTypeElement())
                    .filter(e -> !DISABLED.equals(cachingStrategy(e)))
                    // HC SVNT DRACONES!
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    .filter(AnnotationProcessor.this::checkDeclaredReturnType)
                    .map(e -> methodVisitor(e).visitModuleMethodInClass(newModuleMethod(e)))
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

        abstract class ModuleMethod extends Method {

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
                            .ofNullable(getElements().getTypeElement(moduleClassName(declaredReturnElement)))
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

            @Override
            boolean resolveNonNull() {
                return isAbstract(methodElement()) ||
                        getAnnotation(methodElement(), Cache.class).filter(Cache::nonNull).isPresent();
            }

            @Getter(lazy = true)
            private final String superRef = moduleInterfaceName(getTypeElement()) + ".super";

            Consumer<Output> forAllAccessorMethods() {
                return out -> filteredOverridableMethods(getMakeElement())
                        .filter(e -> !hasParameters(e))
                        .map(e -> {
                            val method = newAccessorMethod(e);
                            return method.apply(method.isCachingDisabled()
                                    ? new DisabledCachingInClassVisitor()
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
                    if (isParameterRef() || isSuperRef()) {
                        return Optional.empty();
                    } else {
                        val element = resolveAccessedElement(getTypeElement());
                        if (!element.isPresent()) {
                            warn("This module interface is missing the dependency returned by ...", getTypeElement());
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
                            .map(Utils::cachingStrategy)
                            .filter(getCachingStrategy()::equals)
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

                Consumer<Output> apply(MethodVisitor v) {
                    return v.visitAccessorMethod(this);
                }
            }
        }

        abstract class Method {

            @Getter(lazy = true)
            private final CachingStrategy cachingStrategy = cachingStrategy(methodElement());

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
            private final String methodTypeParametersDecl = typeParametersWithBoundsDecl(methodElement());

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
