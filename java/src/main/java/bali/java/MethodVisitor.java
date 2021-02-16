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

import bali.java.AnnotationProcessor.ModuleType.FactoryMethod;
import bali.java.AnnotationProcessor.ModuleType.Method;
import bali.java.AnnotationProcessor.ModuleType.ModuleMethod.AccessorMethod;
import bali.java.AnnotationProcessor.ModuleType.ProviderMethod;

import java.util.function.Consumer;

interface MethodVisitor {

    default Consumer<Output> visitAccessorMethod(AccessorMethod m, String in) {
        return visitField(m, in)
                .andThen(visitMethodBegin(m, in))
                .andThen(out -> out.ad(m.getAccessedElementRef()))
                .andThen(visitMethodEnd(m, in));
    }

    default Consumer<Output> visitFactoryMethod(FactoryMethod m) {
        return visitField(m, "    ")
                .andThen(visitMethodBegin(m, "    "))
                .andThen(out -> {
                    out.ad("new ").ad(m.getMakeType()).ad("() {").nl();
                    m.forAllAccessorMethods().accept(out);
                    out.ad("        }");
                })
                .andThen(visitMethodEnd(m, "    "));
    }

    default Consumer<Output> visitProviderMethod(ProviderMethod m) {
        return visitField(m, "    private ")
                .andThen(visitMethodBegin(m, "    "))
                .andThen(out -> {
                    if (m.isSuperRef()) {
                        out.ad(m.getSuperElementRef()).ad(".").ad(m.getMethodName());
                    } else {
                        out.ad("new ").ad(out.makeTypeName(m));
                    }
                    out.ad("()");
                })
                .andThen(visitMethodEnd(m, "    "));
    }

    default Consumer<Output> visitField(Method m, String in) {
        return m.isNonNull() ? visitNonNullField(m, in) : visitNullableField(m, in);
    }

    Consumer<Output> visitNonNullField(Method m, String in);

    Consumer<Output> visitNullableField(Method m, String in);

    default Consumer<Output> visitMethodBegin(Method m, String in) {
        return visitMethodBegin0(m, in)
                .andThen(m.isNonNull() ? visitNonNullMethodBegin(m, in) : visitNullableMethodBegin(m, in));
    }

    default Consumer<Output> visitMethodBegin0(Method m, String in) {
        return out -> out
                .nl()
                .ad(in).ad("@Override").nl()
                .ad(in).ad(m.getMethodModifiers()).ad(m.getMethodTypeParametersDecl()).ad(m.getMethodReturnType()).ad(" ").ad(m.getMethodName()).ad("(").ad(m.getMethodParametersDecl()).ad(") ").ad(m.getMethodThrownTypesDecl()).ad("{").nl();
    }

    Consumer<Output> visitNonNullMethodBegin(Method m, String in);

    Consumer<Output> visitNullableMethodBegin(Method m, String in);

    default Consumer<Output> visitMethodEnd(Method m, String in) {
        return (m.isNonNull() ? visitNonNullMethodEnd(m, in) : visitNullableMethodEnd(m, in))
                .andThen(visitMethodEnd0(m, in));
    }

    default Consumer<Output> visitMethodEnd0(Method m, String in) {
        return out -> out.ad(in).ad("}").nl();
    }

    Consumer<Output> visitNonNullMethodEnd(Method m, String in);

    Consumer<Output> visitNullableMethodEnd(Method m, String in);
}
