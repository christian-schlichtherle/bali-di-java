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
package bali.java.sample.tuple2;

import bali.Lookup;

import java.util.function.Function;

public interface Tuple2<T1, T2> {

    @Lookup(param = "t1", field = "t1")
    T1 getT1();

    @Lookup(method = "getT2", value = "t2")
    T2 getT2();

    <R extends T1> Tuple2<T1, T2> mapT1(Function<T1, R> f);

    <R extends T2> Tuple2<T1, T2> mapT2(Function<T2, R> f);
}
