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
package global.namespace.bali.sample.tuple;

import java.util.function.Function;

interface AbstractTuple2<T1, T2> extends Tuple2<T1, T2> {

    TupleFactory factory();

    @Override
    default <R> Tuple2<R, T2> mapT1(Function<T1, R> f) {
        return factory().tuple(f.apply(t1()), t2());
    }

    @Override
    default <R> Tuple2<T1, R> mapT2(Function<T2, R> f) {
        return factory().tuple(t1(), f.apply(t2()));
    }
}
