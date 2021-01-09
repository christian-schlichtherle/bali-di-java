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
package global.namespace.bali.sample.strange

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class StrangeModuleSpec extends AnyWordSpec {

  "The strange module" should {
    val module = StrangeModule.module
    import module._

    "cache the callable from the provider method" in {
      callable() shouldBe theSameInstanceAs(callable())
    }

    "produce an exception using the callable from the provider method" in {
      intercept[Exception](callable().call).getMessage shouldBe "That's an error."
    }

    "produce 'Hello Christian!' using the callable from the factory method" in {
      callable("Hello Christian!").call shouldBe "Hello Christian!"
    }

    "cache the supplier from the provider method" in {
      supplier() shouldBe theSameInstanceAs(supplier())
    }

    "produce 'Hello world!' using the supplier from the provider method" in {
      supplier().get shouldBe "Hello world!"
    }

    "produce 'Hello Christian!' using the supplier from the factory method" in {
      supplier("Hello Christian!").get shouldBe "Hello Christian!"
    }
  }
}
