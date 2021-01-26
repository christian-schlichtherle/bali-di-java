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
        list.stream().map(elements()::getTypeElement).forEach(this::processElement);

        annotations
                .stream()
                .filter(a -> moduleAnnotationName().equals(a.getQualifiedName()))
                .forEach(a -> roundEnv.getElementsAnnotatedWith(a).forEach(this::processElement));

        if (roundEnv.processingOver()) {
            todo
                    .stream()
                    .map(elements()::getTypeElement)
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
                            val types = types();
                            val t1 = (ExecutableType) types.asMemberOf((DeclaredType) type, e1);
                            val t2 = (ExecutableType) types.asMemberOf((DeclaredType) type, e2);
                            return (types.isSubsignature(t1, t2) || types.isSubsignature(t2, t1))
                                    && !error("Cannot implement this " + (isInterface(element) ? "interface" : "class") + " because ...", element)
                                    && !error("... this method clashes with ...", e1)
                                    && !error("... this method => remove or override these methods.", e2);
                        }));
    }

    private Stream<ExecutableElement> allOverridableMethods(final TypeElement element) {
        return elements()
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
        val moduleElement = Optional.ofNullable(types().asElement(type)).filter(Utils::isModule);
        if (moduleElement.isPresent()) {
            return moduleElement
                    .flatMap(m -> Optional.ofNullable(elements().getTypeElement(moduleImplementationName((TypeElement) m))))
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
                .filter(mirror -> makeAnnotationName().equals(qualifiedNameOf(mirror)))
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
        return (TypeElement) types().asElement(t);
    }

    private boolean error(final CharSequence message, final Element e) {
        messager().printMessage(ERROR, message, e);
        return false;
    }

    private void warn(CharSequence message, Element e) {
        messager().printMessage(WARNING, message, e);
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
        return types().isSubtype(a, b) || error(a + " is not a subtype of " + b + ".", e);
    }

    @RequiredArgsConstructor
    @Getter
    final class ModuleClass implements Consumer<Output> {

        private final TypeElement classElement;

        @Getter(lazy = true)
        private final ModifierSet classModifiers =
                modifiersOf(classElement()).retain(PRIVATE_PROTECTED_PUBLIC).add(ABSTRACT);

        @Getter(lazy = true)
        private final Name classQualifiedName = classElement().getQualifiedName();

        @Getter(lazy = true)
        private final Name classSimpleName = classElement().getSimpleName();

        @Getter(lazy = true)
        private final DeclaredType classType = (DeclaredType) classElement().asType();

        @Getter(lazy = true)
        private final boolean isInterfaceType = isInterface(classElement());

        @Getter(lazy = true)
        private final PackageElement packageElement = elements().getPackageOf(classElement());

        @Getter(lazy = true)
        private final Name packageName = packageElement().getQualifiedName();

        @Getter(lazy = true)
        private final boolean hasAbstractMethods =
                allOverridableMethods(classElement())
                        .filter(Utils::isAbstract)
                        .anyMatch(e ->
                                hasVoidReturnType(e) || hasAnnotation(e, Lookup.class) || !hasDeclaredReturnType(e));

        @Getter(lazy = true)
        private final String classTypeParametersDecl = typeParametersDecl(classElement());

        Consumer<Output> forAllModuleMethods(ClassVisitor v) {
            return forAllModuleMethods0().andThen(out -> out.forAllProviderMethods(v));
        }

        Consumer<Output> forAllModuleMethods0() {
            return out -> filteredOverridableMethods(classElement())
                    // HC SVNT DRACONES!
                    .filter(e -> !hasAnnotation(e, Lookup.class))
                    .filter(AnnotationProcessor.this::checkDeclaredReturnType)
                    .map(e -> hasParameters(e)
                            ? newFactoryMethod(e).apply(new DisabledCachingVisitor())
                            : newProviderMethod(e).apply(methodVisitor(e)))
                    .forEach(c -> c.accept(out));
        }

        FactoryMethod newFactoryMethod(ExecutableElement e) {
            return new FactoryMethod() {

                @Override
                ExecutableElement methodElement() {
                    return e;
                }
            };
        }

        ProviderMethod newProviderMethod(ExecutableElement e) {
            return new ProviderMethod() {

                @Override
                ExecutableElement methodElement() {
                    return e;
                }
            };
        }

        @Override
        public void accept(Output out) {
            new ClassVisitor().visitModuleClass(this).accept(out);
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

        abstract class FactoryMethod extends ModuleMethod {

            @Override
            public Consumer<Output> apply(MethodVisitor v) {
                return v.visitFactoryMethod(this);
            }
        }

        abstract class ProviderMethod extends ModuleMethod {

            @Getter(lazy = true)
            private final String superElementRef = (isInterfaceType() ? classQualifiedName() + "." : "") + "super";

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
            private final Name makeQualifiedName = makeElement().getQualifiedName();

            @Getter(lazy = true)
            private final Name makeSimpleName = makeElement().getSimpleName();

            @Getter(lazy = true)
            private final DeclaredType makeType = resolveMakeType();

            private DeclaredType resolveMakeType() {
                val declaredMakeType = AnnotationProcessor
                        .this
                        .makeType(methodElement())
                        .flatMap(this::parameterizedReturnType)
                        .filter(t -> isSubtype(t, methodReturnType(), methodElement()))
                        .map(TypeMirror.class::cast);
                final TypeMirror declaredReturnType = declaredMakeType.orElseGet(this::methodReturnType);
                val declaredReturnElement = typeElement(declaredReturnType);
                if (isModule(declaredReturnElement)) {
                    val moduleType = Optional
                            .ofNullable(elements().getTypeElement(moduleImplementationName(declaredReturnElement)))
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
                final List<? extends TypeMirror> methodReturnTypeArguments = methodReturnType() instanceof DeclaredType
                        ? ((DeclaredType) methodReturnType()).getTypeArguments()
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
                                .collect(Collectors.toMap(t -> types().asElement(t).getSimpleName(), t -> t));
                val nonVariableMethodReturnTypeArguments =
                        partitionedMethodReturnTypeArguments
                                .get(false);
                val makeTypeArgs = makeElement
                        .getTypeParameters()
                        .stream()
                        .map(Element::getSimpleName)
                        .flatMap(n -> Utils
                                .streamOfNonNull(
                                        () -> variableMethodReturnTypeArguments.get(n),
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

            Consumer<Output> forAllAccessorMethods() {
                return out -> filteredOverridableMethods(makeElement())
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

            abstract class AccessorMethod extends Method {

                @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                @Getter(lazy = true)
                private final Optional<Tuple2<TypeElement, Element>> accessedElement = resolveAccessedElement();

                private Optional<Tuple2<TypeElement, Element>> resolveAccessedElement() {
                    if (isParameterRef() || isSuperRef()) {
                        return Optional.empty();
                    } else {
                        val element = resolveAccessedElement(classElement());
                        if (!element.isPresent()) {
                            warn("This module " + (isInterfaceType() ? "interface" : "class") + " is missing the dependency returned by ...", classElement());
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
                            : isSuperRef()
                            ? (isInterface(makeElement()) ? makeQualifiedName() + "." : "") + "super." + methodName() + "()"
                            : accessedElement().map(Tuple2::t1).orElseGet(ModuleClass.this::classElement).getSimpleName()
                            + (isStaticRef() ? "$" : "$.this")
                            + (isModuleRef() ? "" : "." + (isFieldRef() ? moduleFieldName() + "" : moduleMethodName() + "()"));
                }

                @Getter(lazy = true)
                private final boolean isCachingDisabled = resolveIsCachingDisabled();

                private boolean resolveIsCachingDisabled() {
                    val element = accessedElement().map(Tuple2::t2);
                    return isParameterRef()
                            || isModuleRef()
                            || element.filter(Utils::isFinal).filter(Utils::isField).isPresent()
                            || element
                            .flatMap(Utils::cachingStrategy)
                            .flatMap(s1 -> cachingStrategy(methodElement()).filter(s1::equals))
                            .isPresent();
                }

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

            @Getter(lazy = true)
            private final boolean isSuperRef = !isAbstract(methodElement());

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
            private final ExecutableType methodType = resolveMethodType();

            ExecutableType resolveMethodType() {
                return (ExecutableType) types().asMemberOf(classType(), methodElement());
            }

            @Getter(lazy = true)
            private final String methodTypeParametersDecl = typeParametersDecl(methodElement());

            @Getter(lazy = true)
            private final String methodParametersDecl =
                    mkString(methodParameters().stream().map(var -> var.asType() + " " + var),
                            "", ", ", "");

            @Getter(lazy = true)
            private final String methodThrownTypesDecl =
                    mkString(methodType().getThrownTypes(), "throws ", ", ", " ");
        }
    }
}
