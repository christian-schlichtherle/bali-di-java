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
package global.namespace.bali.sample.greeting;

import bali.Cache;
import bali.Make;
import bali.Module;

import static java.lang.System.out;

@Module
public interface GreetingApp extends Runnable {

    default String format() {
        return "Hello %s!";
    }

    @Cache
    @Make(RealFormatter.class)
    Formatter formatter();

    @Cache
    @Make(RealGreeting.class)
    Greeting greeting();

    @Override
    default void run() {
        out.println(greeting().message("world"));
    }

    static void main(String... args) {
        GreetingApp$.new$().run();
    }
}
