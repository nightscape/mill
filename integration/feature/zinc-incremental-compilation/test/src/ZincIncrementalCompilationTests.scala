package mill.integration

import utest._

import java.nio.file.attribute.FileTime

// Regress test for issue https://github.com/com-lihaoyi/mill/issues/1901
object ZincIncrementalCompilationTests extends IntegrationTestSuite {
  case class FileModificationTimes(
      modelSrc: FileTime,
      appSrc: FileTime,
      testSrc: FileTime,
      modelClass: FileTime,
      appClass: FileTime,
      testClass: FileTime
  )
  val tests: Tests = Tests {
    initWorkspace()
    "incremental compilation only compiles changed files" - {
      val successful = eval("app.test.compile")
      assert(successful)

      val appSrc = wd / "app" / "src" / "main" / "scala" / "App.scala"
      val modelSrc = wd / "app" / "src" / "main" / "scala" / "models" / "TestModel1.scala"
      val testSrc = wd / "app" / "src" / "test" / "scala" / "models" / "ModelTest.scala"
      val classes = wd / "out" / "app" / "compile.dest" / "classes"
      val testClasses = wd / "out" / "app" / "test" / "compile.dest" / "classes"
      val appClass = classes / "app" / "App.class"
      val modelClass = classes / "models" / "Foo.class"
      val testClass = testClasses / "models" / "ModelTest.class"
      assert(Seq(classes, appClass, modelClass, appSrc).forall(os.exists))

      def currentModificationTimes = FileModificationTimes(
        modelSrc = os.stat(modelSrc).mtime,
        appSrc = os.stat(appSrc).mtime,
        testSrc = os.stat(testSrc).mtime,
        modelClass = os.stat(modelClass).mtime,
        appClass = os.stat(appClass).mtime,
        testClass = os.stat(testClass).mtime
      )
      val modificationTimes1 = currentModificationTimes
      println("1" * 80)
      println(modificationTimes1)

      println("** second run **")
      os.write.append(appSrc, "\n ")
      val succ2nd = evalStdout("show", "app.test.testQuickCandidates")
      assert(succ2nd.isSuccess)
      println(succ2nd.out)
      assert(!succ2nd.out.contains("ModelTest"))

      val modificationTimes2 = currentModificationTimes
      println("2" * 80)
      println(modificationTimes2)

      // we changed it
      assert(modificationTimes1.appSrc != modificationTimes2.appSrc)
      // expected to be re-compiled
      assert(modificationTimes1.appClass != modificationTimes2.appClass)
      // expected to be NOT re-compiled
      assert(modificationTimes1.modelClass == modificationTimes2.modelClass)
      // expected to be NOT re-compiled
      assert(modificationTimes1.testClass == modificationTimes2.testClass)

      println("** third run **")
      val modelCode = os.read(modelSrc)
      val modifiedModelCode = modelCode.replace(
        "somefoo",
        "somebar"
      )
      os.write.over(modelSrc, modifiedModelCode)
      // TODO Read implementation of SBT testQuick
      // https://github.com/sbt/sbt/blob/fd20d3039ad06cbee47c6386dc5839060417014b/main/src/main/scala/sbt/Defaults.scala#L758
      val succ3rd = evalStdout("show", "app.test.testQuickCandidates")
      assert(succ3rd.isSuccess)
      println(succ3rd.out)
      assert(succ3rd.out.contains("ModelTest"))

      val modificationTimes3 = currentModificationTimes
      println("3" * 80)
      println(modificationTimes3)
      // we changed it
      assert(modificationTimes3.modelSrc != modificationTimes2.modelSrc)
      // expected to be re-compiled
      assert(modificationTimes3.modelClass != modificationTimes2.modelClass)
      // expected to be NOT re-compiled
      assert(modificationTimes3.appClass == modificationTimes2.appClass)
      // expected to be re-compiled
      assert(modificationTimes3.testClass != modificationTimes2.testClass)
    }
  }
}
