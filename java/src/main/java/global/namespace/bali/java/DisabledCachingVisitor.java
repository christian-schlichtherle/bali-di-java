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
package global.namespace.bali.java;

import global.namespace.bali.java.AnnotationProcessor.ModuleClass.Method;

import java.util.function.Consumer;

final class DisabledCachingVisitor implements MethodVisitor {

    @Override
    public Consumer<Output> visitModuleField(Method m, String in) {
        return visitDependencyField(m, in);
    }

    @Override
    public Consumer<Output> visitModuleCacheBegin(Method m, String in) {
        return visitDependencyCacheBegin(m, in);
    }

    @Override
    public Consumer<Output> visitModuleCacheEnd(Method m, String in) {
        return visitDependencyCacheEnd(m, in);
    }

    @Override
    public Consumer<Output> visitDependencyField(Method m, String in) {
        return out -> {
        };
    }

    @Override
    public Consumer<Output> visitDependencyCacheBegin(Method m, String in) {
        return out -> out.ad(in).ad("    return ");
    }

    @Override
    public Consumer<Output> visitDependencyCacheEnd(Method m, String in) {
        return out -> out.ad(";").nl();
    }
}
