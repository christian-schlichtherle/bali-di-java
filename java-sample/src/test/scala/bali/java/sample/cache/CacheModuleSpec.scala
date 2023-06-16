package bali.java.sample.cache

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
      superDisabled shouldNot be theSameInstanceAs superDisabled
    }

    "cache the time (not thread-safe)" in {
      // TODO: Provoke racing-condition:
      notThreadSafe shouldBe theSameInstanceAs(notThreadSafe)
      superNotThreadSafe shouldBe theSameInstanceAs(superNotThreadSafe)
    }

    "cache the time (thread-safe)" in {
      threadSafe shouldBe theSameInstanceAs(threadSafe)
      superThreadSafe shouldBe theSameInstanceAs(superThreadSafe)
    }

    "cache the time (thread-local)" in {
      var a1: Date = null
      var a2: Date = null

      var b1: Date = null
      var b2: Date = null

      var c1: Date = null
      var c2: Date = null

      var d1: Date = null
      var d2: Date = null

      new Thread(() => {
        a1 = threadLocal
        a2 = threadLocal
      }).tap(_.start()).join()

      new Thread(() => {
        b1 = superThreadLocal
        b2 = superThreadLocal
      }).tap(_.start()).join()

      new Thread(() => {
        c1 = threadLocal
        c2 = threadLocal
      }).tap(_.start()).join()

      new Thread(() => {
        d1 = superThreadLocal
        d2 = superThreadLocal
      }).tap(_.start()).join()

      a1 shouldNot be(null)
      a1 shouldBe theSameInstanceAs(a2)

      b1 shouldNot be(null)
      b1 shouldBe theSameInstanceAs(b2)

      c1 shouldNot be(null)
      c1 shouldBe theSameInstanceAs(c2)

      d1 shouldNot be(null)
      d1 shouldBe theSameInstanceAs(d2)

      Seq(a1, b1, c1, d1).map(System.identityHashCode).toSet.size shouldBe 4
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
