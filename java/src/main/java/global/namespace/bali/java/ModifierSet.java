/*
 * Copyright © 2020 Schlichtherle IT Services
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

import lombok.val;

import javax.lang.model.element.Modifier;
import java.util.Collection;
import java.util.EnumSet;

import static java.util.EnumSet.copyOf;

final class ModifierSet {

    static ModifierSet of(Modifier first, Modifier... rest) {
        return new ModifierSet(EnumSet.of(first, rest));
    }

    static ModifierSet of(Collection<Modifier> modifiers) {
        return new ModifierSet(copyOf(modifiers));
    }

    private final EnumSet<Modifier> modifiers;

    private ModifierSet(final EnumSet<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    boolean isEmpty() {
        return size() == 0;
    }

    int size() {
        return modifiers.size();
    }

    ModifierSet add(final ModifierSet s) {
        val m = copyOf(modifiers);
        m.addAll(s.modifiers);
        return new ModifierSet(m);
    }

    ModifierSet remove(final ModifierSet s) {
        val m = copyOf(modifiers);
        m.removeAll(s.modifiers);
        return new ModifierSet(m);
    }

    ModifierSet retain(final ModifierSet s) {
        val m = copyOf(modifiers);
        m.retainAll(s.modifiers);
        return new ModifierSet(m);
    }

    @Override
    public String toString() {
        return modifiers.stream().reduce("", (s, m) -> s + m + " ", (s1, s2) -> s1 + s2);
    }
}
