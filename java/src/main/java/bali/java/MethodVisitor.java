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

import bali.java.AnnotationProcessor.ModuleInterface.Method;
import bali.java.AnnotationProcessor.ModuleInterface.ModuleMethod;

import java.util.function.Consumer;

interface MethodVisitor {

    Consumer<Output> NOOP = out -> {};

    default Consumer<Output> visitModuleMethod4CompanionInterface(ModuleMethod m) {
        return visitMethodBegin(m)
                .andThen(out -> out.ad("new ").ad(m.isMakeTypeAbstract() ? m.getMakeElementSimpleName() + "$" : m.getLocalMakeType()).ad("()"))
                .andThen(visitMethodEnd(m));
    }

    default Consumer<Output> visitMethod(Method m) {
        return visitField(m, "private ")
                .andThen(visitMethodBegin(m))
                .andThen(out -> out.ad(m.getDependencyCall()))
                .andThen(visitMethodEnd(m))
                .andThen(visitSetter(m));
    }

    default Consumer<Output> visitField(Method m, String prefix) {
        return m.isNullable() ? visitNullableField(m, prefix) : visitNonNullField(m, prefix);
    }

    Consumer<Output> visitNullableField(Method m, String prefix);

    Consumer<Output> visitNonNullField(Method m, String prefix);

    default Consumer<Output> visitMethodBegin(Method m) {
        return visitMethodBegin0(m)
                .andThen(m.isNullable() ? visitNullableMethodBegin(m) : visitNonNullMethodBegin(m));
    }

    Consumer<Output> visitNullableMethodBegin(Method m);

    Consumer<Output> visitNonNullMethodBegin(Method m);

    default Consumer<Output> visitMethodBegin0(Method m) {
        return out -> out
                .nl()
                .ad("@Override").nl()
                .ad(m.getMethodModifiers().toString()).ad(m.getMethodSignatureWithoutModifiers()).ad("{").nl()
                .in();
    }

    default Consumer<Output> visitMethodEnd(Method m) {
        return (m.isNullable() ? visitNullableMethodEnd(m) : visitNonNullMethodEnd(m))
                .andThen(visitMethodEnd0(m));
    }

    Consumer<Output> visitNullableMethodEnd(Method m);

    Consumer<Output> visitNonNullMethodEnd(Method m);

    default Consumer<Output> visitMethodEnd0(Method ignoredM) {
        return out -> out.out().ad("}").nl();
    }

    default Consumer<Output> visitSetter(final Method m) {
        return out -> {
            out
                    .nl()
                    .ad(m.getMethodModifiers().toString()).ad(m.getSetterSignatureWithoutModifiers()).ad(" {").nl();
            (m.isNullable() ? visitNullableSetterBody(m) : visitNonNullSetterBody(m)).accept(out);
            out
                    .ad("}").nl();
        };
    }

    default Consumer<Output> visitNullableSetterBody(Method m) {
        return NOOP;
    }

    default Consumer<Output> visitNonNullSetterBody(Method m) {
        return visitNullableSetterBody(m);
    }
}
