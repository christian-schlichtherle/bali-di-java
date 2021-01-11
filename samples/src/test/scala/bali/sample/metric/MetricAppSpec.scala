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
package bali.sample.metric

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class MetricAppSpec extends AnyWordSpec {

  "The metric app" should {
    val app = MetricApp$.new$
    import app._

    "cache the counter" in {
      counter shouldBe theSameInstanceAs(counter)
    }

    "increment the counter" in {
      counter.incrementAndGet() shouldBe 1
      counter.incrementAndGet() shouldBe 2
    }

    "cache the metric" in {
      metric shouldBe theSameInstanceAs(metric)
    }

    "cache the counter of the metric" in {
      metric.counter shouldBe theSameInstanceAs(metric.counter)
    }

    "use the same counter in the metric" in {
      counter shouldBe theSameInstanceAs(metric.counter)
    }

    "increment the metric" in {
      metric.incrementCounter() shouldBe 3
      metric.incrementCounter() shouldBe 4
    }
  }
}
