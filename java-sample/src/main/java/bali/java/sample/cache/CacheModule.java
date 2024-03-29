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
package bali.java.sample.cache;

import bali.Cache;
import bali.Module;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import static bali.CachingStrategy.*;

@Cache // == @Cache(THREAD_SAFE)
@Module
public interface CacheModule extends SuperModule {

    @Cache(DISABLED)
    Date disabled();

    @Cache(NOT_THREAD_SAFE)
    Date notThreadSafe();

    Date threadSafe();

    void threadSafe(Date date);

    @Cache(THREAD_LOCAL)
    Date threadLocal();

    @Cache
    default Date fixed() {
        return new Date();
    }

    @Cache(THREAD_LOCAL)
    default int randomInt() {
        return ThreadLocalRandom.current().nextInt();
    }
}
