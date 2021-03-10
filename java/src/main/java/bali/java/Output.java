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

import javax.lang.model.element.Name;

final class Output {

    private final StringBuilder out = new StringBuilder();

    private int indentation;
    private boolean nl;

    Output in() {
        return in(1);
    }

    Output in(final int n) {
        indentation += n;
        return this;
    }

    Output out() {
        return out(1);
    }

    Output out(final int n) {
        indentation -= n;
        return this;
    }

    Output ad(final CharSequence s) {
        if (nl) {
            nl = false;
            for (int i = 0; i < indentation; i++) {
                out.append("    ");
            }
        }
        out.append(s);
        return this;
    }

    Output nl() {
        nl = true;
        out.append('\n');
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
