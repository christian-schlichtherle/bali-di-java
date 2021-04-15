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
package bali.java.sample.counter;

import bali.Module;

import static java.lang.System.out;

@Module
public interface CounterApp extends HasCounter, Runnable {

    @Override
    default void run() {
        out.printf("Count: %d\n", counter().incrementAndGet());
        out.printf("Count: %d\n", counter().incrementAndGet());
    }

    static void main(String... args) {
        CounterApp$.new$().run();
    }
}
