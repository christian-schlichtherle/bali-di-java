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

import static bali.CachingStrategy.DISABLED;

final class DisabledCaching4CompanionInterfaceVisitor extends DisabledCachingVisitor {

    @Override
    public Consumer<Output> visitMethodBegin0(Method m) {
        return visitMethodBegin1((ModuleMethod) m);
    }

    private Consumer<Output> visitMethodBegin1(ModuleMethod m) {
        return out -> {
            out.nl();
            if (m.getCachingStrategy() != DISABLED) {
                out.ad("@bali.Cache").ad(m.isNullable() ? "Nullable" : "").ad("(").ad(m.getCachingStrategyName()).ad(")").nl();
            }
            out
                    .ad("@Override").nl()
                    .ad("default ").ad(m.getMethodSignatureWithoutModifiers()).ad("{").nl()
                    .in();
            if (m.isMakeTypeAbstract()) {
                out.ad("final class ").ad(m.getMakeElementSimpleName()).ad("$").ad(m.isMakeTypeInterface() ? " implements " : " extends ").ad(m.getMakeType().toString()).ad(" {").nl().in();
                m.forAllComponentMethods().accept(out);
                out.out().ad("}").nl();
            }
        };
    }
}
