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
package bali.sample.strange;

import bali.Cache;
import bali.Make;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Cache
public interface SuperStrangeModule {

    @Make(ThreadLocalCallable.class)
    Callable<String> callable();

    @Make(ThreadLocalSupplier.class)
    Supplier<String> supplier();

    default String call() throws Exception {
        throw new Exception("That's an error.");
    }

    @Cache
    default String get() {
        return new String("Hello world!");
    }
}
