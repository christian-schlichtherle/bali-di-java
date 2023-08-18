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

import bali.java.AnnotationProcessor.ModuleInterface;

import java.util.function.Consumer;

final class TypeVisitor {

    public Consumer<Output> visitModuleInterface4CompanionInterface(ModuleInterface m) {
        return out -> {
            out
                    .ad("package ").ad(m.getPackageName()).ad(";").nl()
                    .nl()
                    .ad(m.generated()).nl()
                    .ad(m.getModifiers().toString()).ad("interface ").ad(m.getSimpleName()).ad(m.getTypeParametersWithBoundsList().isEmpty() ? "$ " : "$").ad(m.getTypeParametersWithBoundsList()).ad("extends ").ad(m.getLocalDeclaredType()).ad(" {").nl()
                    .in();
            if (!m.hasAbstractMethods()) {
                out
                        .nl()
                        .ad("static ").ad(m.getTypeParametersWithBoundsList()).ad(m.getLocalDeclaredType()).ad(" new$() {").nl()
                        .ad("    return new ").ad(m.getSimpleName()).ad("$$();").nl()
                        .ad("}").nl();
            }
            m.forAllModuleMethods4CompanionInterface().accept(out);
            out.out().ad("}").nl();
        };
    }

    public Consumer<Output> visitModuleInterface4CompanionClass(ModuleInterface m) {
        return out -> {
            out
                    .ad("package ").ad(m.getPackageName()).ad(";").nl()
                    .nl()
                    .ad(m.generated()).nl()
                    .ad(m.getModifiers().toString()).ad(m.hasAbstractMethods() ? "abstract " : "").ad("class ").ad(m.getSimpleName()).ad(m.getTypeParametersWithBoundsList().isEmpty() ? "$$ " : "$$").ad(m.getTypeParametersWithBoundsList()).ad("implements ").ad(m.getSimpleName()).ad(m.getTypeParametersWithoutBoundsList().isEmpty() ? "$ " : "$").ad(m.getTypeParametersWithoutBoundsList()).ad("{").nl()
                    .in();
            m.forAllModuleMethods4CompanionClass().accept(out);
            out.out().ad("}").nl();
        };
    }
}
