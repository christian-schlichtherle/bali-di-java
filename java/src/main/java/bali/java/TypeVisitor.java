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

import bali.java.AnnotationProcessor.ModuleType;

import java.util.function.Consumer;

import static bali.java.Utils.ABSTRACT;

final class TypeVisitor {

    public Consumer<Output> visitModuleInterface(ModuleType c) {
        return out -> {
            out
                    .ad("package ").ad(c.getPackageName()).ad(";").nl()
                    .nl()
                    .ad("/*").nl()
                    .ad(c.generated()).nl()
                    .ad("*/").nl()
                    .ad(c.getTypeModifiers()).ad("interface ").ad(c.getTypeSimpleName()).ad(c.getTypeParametersDecl().isEmpty() ? "$ " : "$").ad(c.getTypeParametersDecl()).ad("extends ").ad(c.getDeclaredType()).ad(" {").nl()
                    .in();
            if (!c.hasAbstractMethods()) {
                out
                        .nl()
                        .ad("static ").ad(c.getTypeParametersDecl()).ad(c.getDeclaredType()).ad(" new$() {").nl()
                        .ad("    return new ").ad(c.getTypeSimpleName()).ad("$$() {").nl()
                        .ad("    };").nl()
                        .ad("}").nl();
            }
            c.forAllModuleMethodsInInterface().accept(out);
            out.out().ad("}").nl();
        };
    }

    public Consumer<Output> visitModuleClass(ModuleType c) {
        return out -> {
            out
                    .ad("package ").ad(c.getPackageName()).ad(";").nl()
                    .nl()
                    .ad("/*").nl()
                    .ad(c.generated()).nl()
                    .ad("*/").nl()
                    .ad(c.getTypeModifiers()).ad("abstract class ").ad(c.getTypeSimpleName()).ad(c.getTypeParametersDecl().isEmpty() ? "$$ " : "$$").ad(c.getTypeParametersDecl()).ad("implements ").ad(c.getTypeSimpleName()).ad(c.getTypeParametersDecl().isEmpty() ? "$ " : "$").ad(c.getTypeParametersDecl()).ad("{").nl()
                    .in();
            c.forAllModuleMethodsInClass().accept(out);
            out.out().ad("}").nl();
        };
    }
}
