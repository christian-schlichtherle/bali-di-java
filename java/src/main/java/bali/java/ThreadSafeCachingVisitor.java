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

final class ThreadSafeCachingVisitor implements MethodVisitor {

    @Override
    public Consumer<Output> visitNonNullField(Method m, String prefix) {
        return out -> out
                .nl()
                .ad(prefix).ad("volatile ").ad(m.getMethodReturnType()).ad(" ").ad(m.getMethodName()).ad(";").nl();
    }

    @Override
    public Consumer<Output> visitNullableField(Method m, String prefix) {
        return out -> out
                .nl()
                .ad(prefix).ad("volatile java.util.function.Supplier<").ad(m.getMethodReturnType()).ad("> ").ad(m.getMethodName()).ad(";").nl();
    }

    @Override
    public Consumer<Output> visitNonNullMethodBegin(Method m) {
        return out -> out
                .ad(m.getMethodReturnType()).ad(" value;").nl()
                .ad("if (null == (value = this.").ad(m.getMethodName()).ad(")) {").nl()
                .ad("    synchronized (this) {").nl()
                .ad("        if (null == (value = this.").ad(m.getMethodName()).ad(")) {").nl()
                .ad("            this.").ad(m.getMethodName()).ad(" = value = ")
                .in(3);
    }

    @Override
    public Consumer<Output> visitNonNullMethodEnd(Method m) {
        return out -> out
                .out(3)
                .ad(";").nl()
                .ad("        }").nl()
                .ad("    }").nl()
                .ad("}").nl()
                .ad("return value;").nl();
    }

    @Override
    public Consumer<Output> visitNullableMethodBegin(Method m) {
        return out -> out
                .ad("java.util.function.Supplier<").ad(m.getMethodReturnType()).ad("> supplier;").nl()
                .ad("if (null == (supplier = this.").ad(m.getMethodName()).ad(")) {").nl()
                .ad("    synchronized (this) {").nl()
                .ad("        if (null == (supplier = this.").ad(m.getMethodName()).ad(")) {").nl()
                .ad("            final ").ad(m.getMethodReturnType()).ad(" value = ")
                .in(3);
    }

    @Override
    public Consumer<Output> visitNullableMethodEnd(Method m) {
        return out -> out
                .out(3)
                .ad(";").nl()
                .ad("            this.").ad(m.getMethodName()).ad(" = supplier = () -> value;").nl()
                .ad("        }").nl()
                .ad("    }").nl()
                .ad("}").nl()
                .ad("return supplier.get();").nl();
    }
}
