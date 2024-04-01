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
package bali.java.sample.tuple2b;

import bali.Lookup;
import bali.Make;
import bali.Module;

@SuppressWarnings("TypeParameterHidesVisibleType")
@Module
public interface Tuple2FactoryWithDefaults<T1, T2> extends Tuple2Factory$ {

    @Lookup(param = "t1")
    T1 getT1();

    @Lookup(param = "t2")
    T2 getT2();

    @Make(ImmutableTuple2.class)
    Tuple2<T1, T2> tuple();

    @Make(ImmutableTuple2.class)
    <T1> Tuple2<T1, T2> tupleFromT1(T1 t1);

    @Make(ImmutableTuple2.class)
    <T2> Tuple2<T1, T2> tupleFromT2(T2 t2);
}
