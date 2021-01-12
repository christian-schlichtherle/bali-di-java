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
package bali.sample.tuple;

import bali.Lookup;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@SuppressWarnings("experimental")
@EqualsAndHashCode
@ToString
abstract class ImmutableTuple2<T1, T2> implements AbstractTuple2<T1, T2> {

    @EqualsAndHashCode.Include
    @ToString.Include(name = "t1")
    @Lookup("t1")
    @Override
    public abstract T1 getT1();

    @EqualsAndHashCode.Include
    @ToString.Include(name = "t2")
    @Lookup(param = "t2", method = "t2")
    @Override
    public abstract T2 getT2();
}
