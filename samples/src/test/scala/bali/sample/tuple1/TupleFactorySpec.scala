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
package bali.sample.tuple1

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class TupleFactorySpec extends AnyWordSpec {

  "The tuple factory" should {
    val factory = TupleFactory$.new$
    import factory._

    "Create a tuple with the given values" in {
      val t = tuple("Hello", " world!")
      t.getT1 shouldBe "Hello"
      t.getT2 shouldBe " world!"
    }

    "Create a tuple with the given values and update them all" in {
      val t = tuple("one", "two").mapT1(_ + " plus ").mapT2(_ + " equals three")
      t.getT1 + t.getT2 shouldBe "one plus two equals three"
    }

    "Create a tuple with the given t1 value and an implicit t2 value" in {
      val t = tupleFromT1("t1")
      t.getT1 shouldBe "t1"
      t.getT2 shouldBe "t2"
    }

    "Create a tuple with the given t2 value and an implicit t1 value" in {
      val t = tupleFromT2("t2")
      t.getT1 shouldBe "t1"
      t.getT2 shouldBe "t2"
    }

    "implement equals and hashCode" in {
      val t1 = tupleFromT1("t1");
      val t2 = tupleFromT2("t2");
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
