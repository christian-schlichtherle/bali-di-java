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

import bali.java.AnnotationProcessor.ModuleClass.FactoryMethod;
import bali.java.AnnotationProcessor.ModuleClass.Method;
import bali.java.AnnotationProcessor.ModuleClass.ModuleMethod.AccessorMethod;
import bali.java.AnnotationProcessor.ModuleClass.ProviderMethod;

import java.util.function.Consumer;

interface MethodVisitor {

    default Consumer<Output> visitAccessorMethod(AccessorMethod m, String in) {
        return visitDependencyField(m, in)
                .andThen(visitMethodBegin(m, in))
                .andThen(visitNullableCacheBegin(m, in))
                .andThen(out -> out.ad(m.accessedElementRef()))
                .andThen(visitNullableCacheEnd(m, in))
                .andThen(visitMethodEnd(m, in));
    }

    default Consumer<Output> visitFactoryMethod(FactoryMethod m) {
        return visitModuleField(m, "    ")
                .andThen(visitMethodBegin(m, "    "))
                .andThen(visitNonNullCacheBegin(m, "    "))
                .andThen(out -> {
                    out.ad("new ").ad(m.makeType()).ad("() {").nl();
                    m.forAllAccessorMethods().accept(out);
                    out.ad("        }");
                })
                .andThen(visitNonNullCacheEnd(m, "    "))
                .andThen(visitMethodEnd(m, "    "));
    }

    default Consumer<Output> visitProviderMethod(ProviderMethod m) {
        return visitModuleField(m, "    private ")
                .andThen(visitMethodBegin(m, "    "))
                .andThen(visitNonNullCacheBegin(m, "    "))
                .andThen(out -> {
                    if (m.isSuperRef()) {
                        out.ad(m.superElementRef()).ad(".").ad(m.methodName());
                    } else {
                        out.ad("new ").ad(out.makeTypeName(m));
                    }
                    out.ad("()");
                })
                .andThen(visitNonNullCacheEnd(m, "    "))
                .andThen(visitMethodEnd(m, "    "));
    }

    default Consumer<Output> visitMethodBegin(Method m, String in) {
        return out -> out
                .nl()
                .ad(in).ad("@Override").nl()
                .ad(in).ad(m.methodModifiers()).ad(m.methodTypeParametersDecl()).ad(m.methodReturnType()).ad(" ").ad(m.methodName()).ad("(").ad(m.methodParametersDecl()).ad(") ").ad(m.methodThrownTypesDecl()).ad("{").nl();
    }

    default Consumer<Output> visitMethodEnd(Method m, String in) {
        return out -> out.ad(in).ad("}").nl();
    }

    Consumer<Output> visitModuleField(Method m, String in);

    Consumer<Output> visitDependencyField(Method m, String in);

    Consumer<Output> visitNonNullCacheBegin(Method m, String in);

    Consumer<Output> visitNonNullCacheEnd(Method m, String in);

    Consumer<Output> visitNullableCacheBegin(Method m, String in);

    Consumer<Output> visitNullableCacheEnd(Method m, String in);
}
