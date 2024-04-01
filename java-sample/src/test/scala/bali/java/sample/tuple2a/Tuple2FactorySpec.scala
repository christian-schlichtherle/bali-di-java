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
package bali.java.sample.tuple2a

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class Tuple2FactorySpec extends AnyWordSpec {

  "The tuple factory" should {
    val factory = Tuple2Factory$.new$
    import factory._

    "Create a tuple with explicit values for t1 and t2" in {
      val t = tuple("Hello", " world!")
      t.getT1 shouldBe "Hello"
      t.getT2 shouldBe " world!"
    }

    "Create a tuple with explicit values for t1 and t2 and update them both" in {
      val t = tuple("one", "two").mapT1(_ + " plus ").mapT2(_ + " equals three")
      t.getT1 + t.getT2 shouldBe "one plus two equals three"
    }

    "implement equals and hashCode" in {
      val t1 = tuple("t1", "t2");
      val t2 = tuple("t1", "t2");
      val t3 = tuple("Hello", " world!")

      t1 shouldBe t2
      t2 shouldBe t1
      t1.hashCode shouldBe t2.hashCode

      t2 shouldNot be(t3)
      t3 shouldNot be(t2)
    }

    "implement toString" in {
      tuple("t1", "t2").toString shouldBe "ImmutableTuple2(t1=t1, t2=t2)"
    }
  }
}
