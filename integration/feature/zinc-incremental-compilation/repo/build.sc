// Issue https://github.com/com-lihaoyi/mill/issues/1901
import mill._
import mill.scalalib.TestModule.Utest
import mill.scalalib._

object app extends SbtModule {

  def scalaVersion = "2.13.8"

  def scalacOptions = Seq("-Vclasspath")

  def ivyDeps = Agg(
    ivy"io.getquill::quill-sql:3.18.0"
  )
  object test extends SbtModuleTests with Utest {
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest:0.8.2"
    )
  }
}
