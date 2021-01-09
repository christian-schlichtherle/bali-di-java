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

import global.namespace.bali.java.AnnotationProcessor.ModuleClass.ProviderMethod;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

final class Output {

    private final StringBuilder out = new StringBuilder();
    private final Map<String, Entry<ProviderMethod, String>> providerMethods = new LinkedHashMap<>();
    private int counter;

    Output ad(Object obj) {
        return ad(String.valueOf(obj));
    }

    Output ad(String str) {
        out.append(str);
        return this;
    }

    Output nl() {
        return ad("\n");
    }

    public void forAllProviderMethods(ClassVisitor v) {
        providerMethods
                .values()
                .forEach(entry -> v.visitProviderMethod(entry.getKey(), entry.getValue()).accept(this));
    }

    String makeTypeName(ProviderMethod m) {
        final String name = m.makeType().toString();
        return m.isMakeTypeAbstract()
                ? providerMethods
                .computeIfAbsent(name, k ->
                        new SimpleImmutableEntry<>(m, m.makeSimpleName() + "$" + ++counter))
                .getValue()
                : name;
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
