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

import java.util.function.Consumer;

final class ThreadLocalCachingVisitor implements MethodVisitor {

    @Override
    public Consumer<Output> visitNonNullField(Method m, String prefix) {
        return out -> out
                .nl()
                .ad(prefix).ad("final java.lang.ThreadLocal<").ad(m.getLocalMethodCacheType()).ad("> ").ad(m.getMethodName()).ad(" = new java.lang.ThreadLocal<>();").nl();
    }

    @Override
    public Consumer<Output> visitNullableField(Method m, String prefix) {
        return out -> out
                .nl()
                .ad(prefix).ad("final java.lang.ThreadLocal<java.util.function.Supplier<").ad(m.getLocalMethodCacheType()).ad(">> ").ad(m.getMethodName()).ad(" = new java.lang.ThreadLocal<>();").nl();
    }

    @Override
    public Consumer<Output> visitNonNullMethodBegin(Method m) {
        return out -> out
                .ad(m.getLocalMethodCacheType()).ad(" value;").nl()
                .ad("if (null == (value = this.").ad(m.getMethodName()).ad(".get())) {").nl()
                .ad("    this.").ad(m.getMethodName()).ad(".set(value = ")
                .in();
    }

    @Override
    public Consumer<Output> visitNonNullMethodEnd(Method m) {
        return out -> out
                .out()
                .ad(");").nl()
                .ad("}").nl()
                .ad("return value;").nl();
    }

    @Override
    public Consumer<Output> visitNullableMethodBegin(Method m) {
        return out -> out
                .ad("java.util.function.Supplier<").ad(m.getLocalMethodCacheType()).ad("> supplier;").nl()
                .ad("if (null == (supplier = this.").ad(m.getMethodName()).ad(".get())) {").nl()
                .ad("    final ").ad(m.getLocalMethodCacheType()).ad(" value = ")
                .in();
    }

    @Override
    public Consumer<Output> visitNullableMethodEnd(Method m) {
        return out -> out
                .out()
                .ad(";").nl()
                .ad("    this.").ad(m.getMethodName()).ad(".set(supplier = () -> value);").nl()
                .ad("}").nl()
                .ad("return supplier.get();").nl();
    }

    @Override
    public Consumer<Output> visitNonNullSetterBody(Method m) {
        return out -> out
                .ad("    this.").ad(m.getMethodName()).ad(".set(value);").nl();
    }

    @Override
    public Consumer<Output> visitNullableSetterBody(Method m) {
        return out -> out
                .ad("    this.").ad(m.getMethodName()).ad(".set(() -> value);").nl();
    }
}
