package bali.sample.cache

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

import java.util.Date
import scala.util.chaining.scalaUtilChainingOps

class CacheModuleSpec extends AnyWordSpec {

  "The cache module" should {
    val module = CacheModule$.new$
    import module._

    "not cache the time" in {
      disabled shouldNot be theSameInstanceAs disabled
    }

    "cache the time (not thread-safe)" in {
      // TODO: Provoke racing-condition:
      notThreadSafe shouldBe theSameInstanceAs(notThreadSafe)
    }

    "cache the time (thread-safe)" in {
      threadSafe shouldBe theSameInstanceAs(threadSafe)
    }

    "cache the time (thread-local)" in {
      var a1: Date = null
      var a2: Date = null

      var b1: Date = null
      var b2: Date = null

      new Thread(() => {
        a1 = threadLocal
        a2 = threadLocal
      }).tap(_.start()).join()

      new Thread(() => {
        b1 = threadLocal
        b2 = threadLocal
      }).tap(_.start()).join()

      a1 shouldNot be(null)
      a1 shouldBe theSameInstanceAs(a2)

      b1 shouldNot be(null)
      b1 shouldBe theSameInstanceAs(b2)

      a1 shouldNot be theSameInstanceAs b1
    }

    "cache the time (fixed)" in {
      fixed shouldBe theSameInstanceAs(fixed)
    }

    "cache the random integer (thread-local)" in {
      var a1: Int = 0
      var a2: Int = 0

      var b1: Int = 0
      var b2: Int = 0

      new Thread(() => {
        a1 = randomInt
        a2 = randomInt
      }).tap(_.start()).join()

      new Thread(() => {
        b1 = randomInt
        b2 = randomInt
      }).tap(_.start()).join()

      a1 shouldEqual a2

      b1 shouldEqual b2

      a1 should not equal b1
    }
  }
}
