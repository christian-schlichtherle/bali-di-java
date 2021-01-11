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

import bali.java.AnnotationProcessor.ModuleClass.Method;

import java.util.function.Consumer;

final class ThreadLocalCachingVisitor implements MethodVisitor {

    @Override
    public Consumer<Output> visitModuleField(Method m, String in) {
        return out -> out
                .nl()
                .ad(in).ad("final java.lang.ThreadLocal<").ad(m.methodReturnType()).ad("> ").ad(m.methodName()).ad(" = new java.lang.ThreadLocal<>();").nl();
    }

    @Override
    public Consumer<Output> visitModuleCacheBegin(Method m, String in) {
        return out -> out
                .ad(in).ad("    ").ad(m.methodReturnType()).ad(" ").ad(m.methodName()).ad(";").nl()
                .ad(in).ad("    if (null == (").ad(m.methodName()).ad(" = this.").ad(m.methodName()).ad(".get())) {").nl()
                .ad(in).ad("        this.").ad(m.methodName()).ad(".set(").ad(m.methodName()).ad(" = ");
    }

    @Override
    public Consumer<Output> visitModuleCacheEnd(Method m, String in) {
        return out -> out
                .ad(");").nl()
                .ad(in).ad("    }").nl()
                .ad(in).ad("    return ").ad(m.methodName()).ad(";").nl();
    }

    @Override
    public Consumer<Output> visitDependencyField(Method m, String in) {
        return out -> out
                .nl()
                .ad(in).ad("final java.lang.ThreadLocal<java.util.function.Supplier<").ad(m.methodReturnType()).ad(">> ").ad(m.methodName()).ad(" = new java.lang.ThreadLocal<>();").nl();
    }

    @Override
    public Consumer<Output> visitDependencyCacheBegin(Method m, String in) {
        return out -> out
                .ad(in).ad("    java.util.function.Supplier<").ad(m.methodReturnType()).ad("> supplier;").nl()
                .ad(in).ad("    if (null == (supplier = this.").ad(m.methodName()).ad(".get())) {").nl()
                .ad(in).ad("        final ").ad(m.methodReturnType()).ad(" value = ");
    }

    @Override
    public Consumer<Output> visitDependencyCacheEnd(Method m, String in) {
        return out -> out
                .ad(";").nl()
                .ad(in).ad("        this.").ad(m.methodName()).ad(".set(supplier = () -> value);").nl()
                .ad(in).ad("    }").nl()
                .ad(in).ad("    return supplier.get();").nl();
    }
}
