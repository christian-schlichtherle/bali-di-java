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

import global.namespace.bali.java.AnnotationProcessor.ModuleClass;
import global.namespace.bali.java.AnnotationProcessor.ModuleClass.ProviderMethod;

import java.util.function.Consumer;

final class ClassVisitor {

    Consumer<Output> visitModuleClass(ModuleClass c) {
        return out -> {
            out
                    .ad("package ").ad(c.packageName()).ad(";").nl()
                    .nl()
                    .ad("final class ").ad(c.classSimpleName()).ad("$ ").ad(c.isInterfaceType() ? "implements " : "extends ").ad(c.classType()).ad(" {").nl()
                    .nl()
                    .ad("    static ").ad(c.classType()).ad(" new$() {").nl()
                    .ad("        return new ").ad(c.classSimpleName()).ad("$();").nl()
                    .ad("    }").nl()
                    .nl()
                    .ad("    private ").ad(c.classSimpleName()).ad("$() {").nl()
                    .ad("    }").nl();
            c.forAllModuleMethods(this).accept(out);
            out.ad("}").nl();
        };
    }

    Consumer<Output> visitProviderMethod(ProviderMethod m, String makeTypeSimpleName) {
        return out -> {
            out
                    .nl()
                    .ad("    private final class ").ad(makeTypeSimpleName).ad(" ").ad(m.isMakeTypeInterface() ? "implements " : "extends ").ad(m.makeType()).ad(" {").nl();
            m.forAllAccessorMethods().accept(out);
            out.ad("    }").nl();
        };
    }
}
