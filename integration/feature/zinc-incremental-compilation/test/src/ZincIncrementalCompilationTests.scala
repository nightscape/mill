package mill.integration

import utest._

// Regress test for issue https://github.com/com-lihaoyi/mill/issues/1901
object ZincIncrementalCompilationTests extends IntegrationTestSuite {
  val tests: Tests = Tests {
    initWorkspace()
    "incremental compilation only compiles changed files" - {
      val successful = eval("app.test.compile")
      assert(successful)

      val appSrc = wd / "app" / "src" / "main" / "scala" / "App.scala"
      val modelSrc = wd / "app" / "src" / "main" / "scala" / "models" / "TestModel1.scala"
      val classes = wd / "out" / "app" / "compile.dest" / "classes"
      val testClasses = wd / "out" / "app" / "test" / "compile.dest" / "classes"
      val app = classes / "app" / "App.class"
      val model = classes / "models" / "Foo.class"
      val test = testClasses / "models" / "ModelTest.class"
      assert(Seq(classes, app, model, appSrc).forall(os.exists))

      val appSrcInfo1 = os.stat(appSrc)
      val appInfo1 = os.stat(app)
      val modelInfo1 = os.stat(model)
      val testInfo1 = os.stat(test)

      println("** second run **")
      os.write.append(appSrc, "\n ")
      val succ2nd = eval("app.test.testQuick")
      assert(succ2nd)

      val appSrcInfo2 = os.stat(appSrc)
      val modelSrcInfo2 = os.stat(modelSrc)
      val appInfo2 = os.stat(app)
      val modelInfo2 = os.stat(model)
      val testInfo2 = os.stat(test)

      // we changed it
      assert(appSrcInfo1.mtime != appSrcInfo2.mtime)
      // expected to be re-compiled
      assert(appInfo1.ctime != appInfo2.ctime)
      // expected to be NOT re-compiled
      assert(modelInfo1.ctime == modelInfo2.ctime)
      // expected to be NOT re-compiled
      assert(testInfo1.ctime == testInfo2.ctime)

      println("** third run **")
      val modelCode = os.read(modelSrc)
      val modifiedModelCode = modelCode.replace(
        "= 0L",
        "= 1L"
      )
      // TODO Read implementation of SBT testQuick
      // https://github.com/sbt/sbt/blob/fd20d3039ad06cbee47c6386dc5839060417014b/main/src/main/scala/sbt/Defaults.scala#L758
      os.write.over(modelSrc, modifiedModelCode)
      val succ3rd = eval("app.test.testQuick")
      assert(succ3rd)

      val modelSrcInfo3 = os.stat(modelSrc)
      val appInfo3 = os.stat(app)
      val modelInfo3 = os.stat(model)
      val testInfo3 = os.stat(test)

      // we changed it
      assert(modelSrcInfo3.mtime != modelSrcInfo2.mtime)
      // expected to be re-compiled
      assert(modelInfo3.ctime != modelInfo2.ctime)
      // expected to be NOT re-compiled
      assert(appInfo3.ctime == appInfo2.ctime)
      // expected to be re-compiled
      assert(testInfo3.ctime != testInfo2.ctime)
    }
  }
}
