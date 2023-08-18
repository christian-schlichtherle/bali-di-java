package bali.java.sample.naming

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class NamingModuleSpec extends AnyWordSpec {

  "The naming module" should {
    val module = NamingModule$.new$

    "yield the expected simple names" in {
      module.simple shouldBe "NamingModule$$"
      module.name1.simple shouldBe "WhatsMyName"
      module.name2.simple shouldBe "WhatsMyName"
    }

    "yield the expected qualified names" in {
      module.qualified should endWith(".NamingModule$$")
      module.name1.qualified should endWith(".NamingModule$$1WhatsMyName")
      module.name2.qualified should endWith(".NamingModule$$2WhatsMyName")
    }
  }
}
