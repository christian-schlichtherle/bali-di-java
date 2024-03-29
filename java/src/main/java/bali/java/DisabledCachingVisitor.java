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

class DisabledCachingVisitor implements MethodVisitor {

    @Override
    public Consumer<Output> visitNullableField(Method m, String prefix) {
        return NOOP;
    }

    @Override
    public Consumer<Output> visitNullableMethodBegin(Method m) {
        return out -> out.ad("return ");
    }

    @Override
    public Consumer<Output> visitNullableMethodEnd(Method m) {
        return out -> out.ad(";").nl();
    }

    @Override
    public Consumer<Output> visitNonNullField(Method m, String prefix) {
        return visitNullableField(m, prefix);
    }

    @Override
    public Consumer<Output> visitNonNullMethodBegin(Method m) {
        return visitNullableMethodBegin(m);
    }

    @Override
    public Consumer<Output> visitNonNullMethodEnd(Method m) {
        return visitNullableMethodEnd(m);
    }

    @Override
    public Consumer<Output> visitSetter(Method m) {
        return NOOP;
    }
}
