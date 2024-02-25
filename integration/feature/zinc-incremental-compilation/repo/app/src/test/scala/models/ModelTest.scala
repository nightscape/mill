package models

import java.time.LocalDateTime
import utest._

object ModelTest extends TestSuite {
  val tests = Tests {
    test("test") {
      val model = Foo(gmtCreate = LocalDateTime.now)
      assert(model.id == 0L)
    }
  }
}
