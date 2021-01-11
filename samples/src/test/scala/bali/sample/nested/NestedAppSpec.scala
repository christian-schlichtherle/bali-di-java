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
package bali.sample.nested

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class NestedAppSpec extends AnyWordSpec {

  "The static module in the nested app" should {
    val module = NestedApp.StaticModule.INSTANCE
    import module._

    "produce 'Hello world!'" in {
      message.get shouldBe "Hello world!"
    }
  }
}
