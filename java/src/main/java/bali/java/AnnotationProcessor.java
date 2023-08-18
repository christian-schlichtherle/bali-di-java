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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bali.CachingStrategy.DISABLED;
import static bali.java.Utils.*;
import static java.util.Collections.unmodifiableList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;
import static lombok.AccessLevel.PRIVATE;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@SupportedAnnotationTypes("bali.*")
public final class AnnotationProcessor extends AbstractProcessor {

    @Getter(lazy = true, value = PRIVATE)
    private final Elements elements = processingEnv.getElementUtils();

    @Getter(lazy = true, value = PRIVATE)
    private final Filer filer = processingEnv.getFiler();

    @Getter(lazy = true, value = PRIVATE)
    private final Name makeAnnotationName = getElements().getName(Make.class.getName());

    @Getter(lazy = true, value = PRIVATE)
    private final Messager messager = processingEnv.getMessager();

    @Getter(lazy = true, value = PRIVATE)
    private final Name moduleAnnotationName = getElements().getName(Module.class.getName());

    @Getter(lazy = true, value = PRIVATE)
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
        val moduleInterface = new ModuleInterface(e);
        val iface = new Output();
        val klass = new Output();

        save = true;
        typeVisitor.visitModuleInterface4CompanionInterface(moduleInterface).accept(iface); // may set save = false as side effect
        if (save) {
            typeVisitor.visitModuleInterface4CompanionClass(moduleInterface).accept(klass); // dito
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
                .filter(e -> isAbstract(e) || isParameterLess(e) && cachingStrategy(e) != DISABLED)
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
                        .flatMap(m -> Optional.ofNullable(getElements().getTypeElement(companionClassName((QualifiedNameable) m))))
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
        return (TypeElement) element(t);
    }

    private Element element(TypeMirror t) {
        return getTypes().asElement(t);
    }

    private boolean error(final CharSequence message, final Element e) {
        getMessager().printMessage(ERROR, message, e);
        return false;
    }

    private void warn(CharSequence message, Element e) {
        getMessager().printMessage(WARNING, message, e);
    }

    private boolean checkCacheableReturnType(ExecutableElement e) {
        return hasCacheableReturnType(e) || error("Cannot cache return value.", e);
    }

    private String typeParametersWithoutBoundsList(Parameterizable parameterizable) {
        return typeParametersList(parameterizable, Function.identity());
    }

    private String typeParametersWithBoundsList(Parameterizable parameterizable) {
        return typeParametersList(parameterizable,
                p -> p + mkString(p.getBounds().stream().filter(t -> !isObject(t)), " extends ", " & ", ""));
    }

    private String typeParametersList(Parameterizable parameterizable, Function<? super TypeParameterElement, ?> f) {
        return mkString(parameterizable.getTypeParameters().stream().map(f), "<", ", ", "> ");
    }

    private boolean isSubtype(TypeMirror a, TypeMirror b, Element e) {
        return getTypes().isSubtype(a, b) || error(a + " is not a subtype of " + b + ".", e);
    }

    private PackageElement packageOf(Element e) {
        return getElements().getPackageOf(e);
    }

    @Getter
    @RequiredArgsConstructor
    final class ModuleInterface {

        private final TypeElement element;

        @Getter(lazy = true)
        private final ModifierSet modifiers = modifiersOf(getElement()).retain(PRIVATE_PROTECTED_PUBLIC);

        @Getter(lazy = true)
        private final Name simpleName = getElement().getSimpleName();

        @Getter(lazy = true)
        private final DeclaredType declaredType = (DeclaredType) getElement().asType();

        @Accessors(fluent = true)
        @Getter(lazy = true)
        private final boolean hasAbstractMethods =
                allOverridableMethods(getElement())
                        .filter(Utils::isAbstract)
                        .anyMatch(e -> hasAnnotation(e, Lookup.class) || !hasCacheableReturnType(e));

        @Getter(lazy = true)
        private final String localDeclaredType = local(getDeclaredType());

        @Getter(lazy = true)
        private final PackageElement packageElement = packageOf(getElement());

        @Getter(lazy = true)
        private final Name packageName = getPackageElement().getQualifiedName();

        @Getter(lazy = true)
        private final String typeParametersWithoutBoundsList = typeParametersWithoutBoundsList(getElement());

        @Getter(lazy = true)
        private final String typeParametersWithBoundsList = typeParametersWithBoundsList(getElement());

        String generated() {
            return String.format(Locale.ENGLISH,
                    "@bali.Generated(\n" +
                            "    processor = \"%s\",\n" +
                            "    round = %d,\n" +
                            "    timestamp = \"%s\",\n" +
                            "    version = \"%s\"\n" +
                            ")",
                    AnnotationProcessor.class.getName(),
                    round,
                    OffsetDateTime.now(),
                    RESOURCE_BUNDLE.getString("version")
            );
        }

        Consumer<Output> forAllModuleMethods4CompanionInterface() {
            return out -> filteredOverridableMethods(getElement())
                    .filter(Utils::isAbstract)
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    // HC SVNT DRACONES!
                    .filter(AnnotationProcessor.this::checkCacheableReturnType)
                    .map(e -> new DisabledCaching4CompanionInterfaceVisitor().visitModuleMethod4CompanionInterface(newModuleMethod(e)))
                    .forEach(c -> c.accept(out));
        }

        Consumer<Output> forAllModuleMethods4CompanionClass() {
            return out -> filteredOverridableMethods(getElement())
                    .filter(Utils::isParameterLess)
                    .filter(e -> cachingStrategy(e) != DISABLED)
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    // HC SVNT DRACONES!
                    .filter(AnnotationProcessor.this::checkCacheableReturnType)
                    .map(e -> methodVisitor(e).visitModuleMethod4CompanionClass(newModuleMethod(e)))
                    .forEach(c -> c.accept(out));
        }

        private String local(Object o) {
            return local(o.toString());
        }

        private String local(String s) {
            val prefix = getPackageName() + ".";
            return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
        }

        ModuleMethod newModuleMethod(ExecutableElement e) {
            return new ModuleMethod() {

                @Override
                ExecutableElement getMethodElement() {
                    return e;
                }
            };
        }

        abstract class ModuleMethod extends Method {

            @Getter(lazy = true)
            private final Name localMakeElementName =
                    getMakeElementPackage().equals(getPackageElement())
                            ? getMakeElement().getSimpleName()
                            : ((QualifiedNameable) getMakeElement()).getQualifiedName();

            @Getter(lazy = true)
            private final String localMakeType =
                    getMakeElementPackage().equals(getPackageElement())
                            ? local(getMakeType())
                            : getMakeType().toString();

            @Getter(lazy = true)
            private final boolean makeTypeAbstract = isAbstract(getMakeElement());

            @Getter(lazy = true)
            private final boolean makeTypeInterface = isInterface(getMakeElement());

            @Getter(lazy = true)
            private final Element makeElement = element(getMakeType());

            @Getter(lazy = true)
            private final Name makeElementSimpleName = getMakeElement().getSimpleName();

            @Getter(lazy = true)
            private final PackageElement makeElementPackage = packageOf(getMakeElement());

            @Getter(lazy = true)
            private final TypeMirror makeType = resolveMakeType();

            private TypeMirror resolveMakeType() {
                val declaredMakeType = AnnotationProcessor
                        .this
                        .makeType(getMethodElement())
                        .flatMap(this::parameterizedReturnType)
                        .filter(t -> isSubtype(t, getMethodReturnType(), getMethodElement()))
                        .map(TypeMirror.class::cast);
                final TypeMirror declaredReturnType = declaredMakeType.orElseGet(this::getMethodReturnType);
                val declaredReturnElement = element(declaredReturnType);
                if (isModule(declaredReturnElement)) {
                    val moduleType = Optional
                            .ofNullable(getElements().getTypeElement(companionClassName((TypeElement) declaredReturnElement)))
                            .map(Element::asType)
                            .flatMap(this::parameterizedReturnType);
                    if (moduleType.isPresent()) {
                        return moduleType.get();
                    }
                }
                return declaredReturnType;
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
                    error("Incompatible type parameters.", getMethodElement());
                    return Optional.empty();
                }
            }

            @Override
            boolean resolveNullable() {
                return !isAbstract(getMethodElement()) && super.resolveNullable();
            }

            @Getter(lazy = true)
            private final String companionInterfaceRef = getElement().getSimpleName() + "$.super";

            Consumer<Output> forAllComponentMethods() {
                return out -> filteredOverridableMethods((TypeElement) getMakeElement())
                        // HC SVNT DRACONES!
                        .map(e -> new Tuple2<>(newComponentMethod(e), e))
                        .filter(t -> t.getT1().isCachingDisabled() || checkCacheableReturnType(t.getT2()))
                        .map(t ->
                                (t.getT1().isCachingDisabled() ? new DisabledCachingVisitor() : methodVisitor(t.getT2()))
                                        .visitComponentMethod(t.getT1()))
                        .forEach(c -> c.accept(out));
            }

            ComponentMethod newComponentMethod(ExecutableElement e) {
                return new ComponentMethod() {

                    @Override
                    ExecutableElement getMethodElement() {
                        return e;
                    }
                };
            }

            abstract class ComponentMethod extends Method {

                @Getter(lazy = true)
                private final Optional<Tuple2<TypeElement, Element>> accessedElement = resolveAccessedElement();

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement() {
                    if (isParameterRef() || isSuperRef()) {
                        return Optional.empty();
                    } else {
                        val element = resolveAccessedElement(getElement());
                        if (!element.isPresent()) {
                            warn("This module interface is missing the dependency returned by ...", getElement());
                            warn("... this dependency method.", getMethodElement());
                        }
                        return element;
                    }
                }

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement(Element e) {
                    for (; e instanceof TypeElement; e = e.getEnclosingElement()) {
                        val where = (TypeElement) e;
                        if (getTypes().isSubtype(getDeclaredType(), getMethodReturnType())) {
                            return Optional.of(new Tuple2<>(where, where));
                        }
                        val what = findAccessedElement(where);
                        if (what.isPresent()) {
                            return what;
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
                    return Stream
                            .<Function<Element, Boolean>>of(Utils::isMethod, Utils::isField)
                            .flatMap(f -> members.stream().filter(f::apply))
                            .map(e -> new Tuple2<TypeElement, Element>(where, e))
                            .findFirst();
                }

                @Getter(lazy = true)
                private final String accessedElementRef = resolveAccessedElementRef();

                private String resolveAccessedElementRef() {
                    return isParameterRef()
                            ? getModuleParamName().toString()
                            : isSuperRef()
                            ? (isMakeTypeInterface() ? getLocalMakeElementName() + "." : "") + "super." + getMethodName() + "(" + getMethodParametersWithoutTypesList() + ")"
                            : getAccessedElement().map(Tuple2::getT1).orElseGet(ModuleInterface.this::getElement).getSimpleName()
                            + (isStaticRef() ? "$" : "$.this")
                            + (isModuleRef() ? "" : "." + (isFieldRef() ? getModuleFieldName() + "" : getModuleMethodName() + "(" + getMethodParametersWithoutTypesList() + ")"));
                }

                @Getter(lazy = true)
                private final boolean cachingDisabled = resolveCachingDisabled();

                private boolean resolveCachingDisabled() {
                    val e = getAccessedElement().map(Tuple2::getT2);
                    return isParameterRef()
                            || isModuleRef()
                            || e.filter(Utils::isFinal).filter(Utils::isField).isPresent()
                            || e.map(Utils::cachingStrategy).filter(getCachingStrategy()::equals).isPresent()
                            || e.filter(Utils::isMethod).map(ExecutableElement.class::cast).filter(ee -> !isParameterLess(ee)).isPresent();
                }

                @Getter(lazy = true)
                private final boolean fieldRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isField).isPresent();

                @Getter(lazy = true)
                private final boolean moduleRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isType).isPresent();

                @Getter(lazy = true)
                private final boolean parameterRef =
                        ModuleMethod
                                .this
                                .getMethodParameters()
                                .stream()
                                .map(Element::getSimpleName)
                                .anyMatch(getModuleParamName()::equals);

                @Getter(lazy = true)
                private final boolean staticRef =
                        getAccessedElement().map(Tuple2::getT2).filter(Utils::isStatic).isPresent();

                @Getter(lazy = true)
                private final boolean superRef = !isAbstract(getMethodElement());

                @Override
                boolean resolveNullable() {
                    val optAccessedElement = getAccessedElement();
                    if (optAccessedElement.isPresent()) {
                        val accessedElement = optAccessedElement.get().getT2();
                        return !isAbstract(accessedElement) &&
                                getAnnotation(accessedElement, CacheNullable.class).isPresent();
                    } else {
                        return super.resolveNullable();
                    }
                }

                @Override
                ExecutableType resolveMethodType() {
                    return (ExecutableType) getTypes().asMemberOf((DeclaredType) getMakeType(), getMethodElement());
                }

                @Getter(lazy = true)
                private final Optional<Lookup> lookup = getAnnotation(getMethodElement(), Lookup.class);

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
            }
        }

        abstract class Method {

            @Getter(lazy = true)
            private final CachingStrategy cachingStrategy = cachingStrategy(getMethodElement());

            @Getter(lazy = true)
            private final String cachingStrategyName = CACHING_STRATEGY_CLASSNAME + "." + getCachingStrategy();

            @Getter(lazy = true)
            private final String localMethodCacheType =
                    getMethodCacheElementPackage().filter(p -> p.equals(getPackageElement())).isPresent()
                            ? local(getMethodCacheType())
                            : getMethodCacheType().toString();

            @Getter(lazy = true)
            private final String localMethodReturnType =
                    getMethodReturnElementPackage().filter(p -> p.equals(getPackageElement())).isPresent()
                            ? local(getMethodReturnType())
                            : getMethodReturnType().toString();

            @Getter(lazy = true)
            private final Optional<Element> methodCacheElement =
                    Optional.ofNullable(getTypes().asElement(getMethodCacheType()));

            @Getter(lazy = true)
            private final Optional<PackageElement> methodCacheElementPackage =
                    getMethodCacheElement().map(AnnotationProcessor.this::packageOf);

            @Getter(lazy = true)
            private final TypeMirror methodCacheType =
                    isPrimitiveMethodReturnType()
                            ? getTypes().boxedClass((PrimitiveType) getMethodReturnType()).asType()
                            : getMethodReturnType();

            abstract ExecutableElement getMethodElement();

            @Getter(lazy = true)
            private final ModifierSet methodModifiers = modifiersOf(getMethodElement()).retain(PROTECTED_PUBLIC);

            @Getter(lazy = true)
            private final Name methodName = getMethodElement().getSimpleName();

            @Getter(lazy = true)
            private final List<? extends VariableElement> methodParameters =
                    unmodifiableList(getMethodElement().getParameters());

            @Getter(lazy = true)
            private final String methodParametersWithoutTypesList =
                    mkString(getMethodParameters(), "", ", ", "");

            @Getter(lazy = true)
            private final String methodParametersWithTypesList =
                    mkString(getMethodParameters().stream().map(var -> var.asType() + " " + var),
                            "", ", ", "");

            @Getter(lazy = true)
            private final Optional<Element> methodReturnElement =
                    Optional.ofNullable(getTypes().asElement(getMethodReturnType()));

            @Getter(lazy = true)
            private final Optional<PackageElement> methodReturnElementPackage =
                    getMethodReturnElement().map(AnnotationProcessor.this::packageOf);

            @Getter(lazy = true)
            private final TypeMirror methodReturnType = getMethodType().getReturnType();

            @Getter(lazy = true)
            private final String methodSignatureWithoutModifiers =
                    getMethodTypeParametersWithBoundsList() + getLocalMethodReturnType() + " " + getMethodName() +
                            "(" + getMethodParametersWithTypesList() + ") " + getMethodThrowsList();

            @Getter(lazy = true)
            private final String methodThrowsList =
                    mkString(getMethodType().getThrownTypes(), "throws ", ", ", " ");

            @Getter(lazy = true)
            private final ExecutableType methodType = resolveMethodType();

            ExecutableType resolveMethodType() {
                return (ExecutableType) getTypes().asMemberOf(getDeclaredType(), getMethodElement());
            }

            @Getter(lazy = true)
            private final String methodTypeParametersWithBoundsList = typeParametersWithBoundsList(getMethodElement());

            @Getter(lazy = true)
            private final boolean nullable = resolveNullable();

            boolean resolveNullable() {
                return !isPrimitiveMethodReturnType() &&
                        getAnnotation(getMethodElement(), CacheNullable.class).isPresent();
            }

            @Getter(lazy = true)
            private final boolean primitiveMethodReturnType = getMethodReturnType().getKind().isPrimitive();
        }
    }
}
