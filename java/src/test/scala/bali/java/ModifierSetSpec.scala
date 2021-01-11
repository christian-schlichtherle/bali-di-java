package bali.java

import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.wordspec.AnyWordSpec

import java.util.{EnumSet => Enums}
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier._

class ModifierSetSpec extends AnyWordSpec {

  "A ModifierSet" should {
    "have the expected string representation" in {
      val tests = Table(
        ("modifiers", "string"),
        (Enums.noneOf(classOf[Modifier]), ""),
        (Enums.of(FINAL), "final "),
        (Enums.of(PUBLIC, FINAL), "public final "),
      )
      forAll(tests) { (modifiers, string) =>
        ModifierSet.of(modifiers).toString shouldBe string
      }
    }
  }
}
