package mill.contrib

import java.io.PrintWriter
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.Executors

import ch.epfl.scala.bsp4j._
import mill._
import mill.define.{Command, Discover, ExternalModule}
import mill.eval.Evaluator
import org.eclipse.lsp4j.jsonrpc.Launcher
import upickle.default._

import scala.collection.JavaConverters._
import scala.concurrent.CancellationException

case class BspConfigJson(name: String,
                         argv: Seq[String],
                         version: String,
                         bspVersion: String,
                         languages: Seq[String])
  extends BspConnectionDetails(name, argv.asJava, version, bspVersion, languages.asJava) {
}

object BspConfigJson {
  implicit val rw: ReadWriter[BspConfigJson] = macroRW
}

object BSP extends ExternalModule {

  implicit def millScoptEvaluatorReads[T] = new mill.main.EvaluatorScopt[T]()

  lazy val millDiscover: Discover[BSP.this.type] = Discover[this.type]
  val version = "1.0.0"
  val bspProtocolVersion = "2.0.0"
  val languages = List("scala", "java")

  /**
    * Installs the mill-bsp server. It creates a json file
    * with connection details in the ./.bsp directory for
    * a potential client to find.
    *
    * If a .bsp folder with a connection file already
    * exists in the working directory, it will be
    * overwritten and a corresponding message will be displayed
    * in stdout.
    *
    * If the creation of the .bsp folder fails due to any other
    * reason, the message and stacktrace of the exception will be
    * printed to stdout.
    *
    */
  def install(ev: Evaluator): Command[Unit] = T.command {
    val bspDirectory = os.pwd / ".bsp"
    if (!os.exists(bspDirectory)) os.makeDir.all(bspDirectory)
    try {
      os.write(bspDirectory / "mill.json", createBspConnectionJson())
    } catch {
      case e: FileAlreadyExistsException =>
        println("The bsp connection json file probably exists already - will be overwritten")
        os.remove(bspDirectory / "mill.json")
        os.write(bspDirectory / "mill.json", createBspConnectionJson())
      case e: Exception =>
        println("An exception occurred while installing mill-bsp")
        e.printStackTrace()
    }

  }

  // creates a Json with the BSP connection details
  def createBspConnectionJson(): String = {
    val millPath = scala.sys.props.get("MILL_CLASSPATH").getOrElse(System.getProperty("MILL_CLASSPATH"))
    val millVersion = scala.sys.props.get("MILL_VERSION").getOrElse(System.getProperty("MILL_VERSION"))
    write(BspConfigJson("mill-bsp",
                        List(whichJava,
                             s"-DMILL_CLASSPATH=$millPath",
                             s"-DMILL_VERSION=$millVersion",
                             "-Djna.nosys=true",
                             "-cp",
                             millPath,
                             "mill.MillMain",
                             "mill.contrib.BSP/start"),
                        version,
                        bspProtocolVersion,
                        languages))
  }

  // computes the path to the java executable
  def whichJava: String = {
    if (scala.sys.props.contains("JAVA_HOME")) scala.sys.props("JAVA_HOME") else "java"
  }

  /**
    * Computes a mill command which starts the mill-bsp
    * server and establishes connection to client. Waits
    * until a client connects and ends the connection
    * after the client sent an "exit" notification
    *
    * @param ev Environment, used by mill to evaluate commands
    * @return: mill.Command which executes the starting of the
    *          server
    */
  def start(ev: Evaluator): Command[Unit] = T.command {
    val eval = new Evaluator(ev.home, ev.outPath, ev.externalOutPath, ev.rootModule, ev.log, ev.classLoaderSig,
                             ev.workerCache, ev.env, false)
    val millServer = new mill.contrib.bsp.MillBuildServer(eval, bspProtocolVersion, version, languages)
    val executor = Executors.newCachedThreadPool()

    val stdin = System.in
    val stdout = System.out
    try {
      val launcher = new Launcher.Builder[BuildClient]()
        .setOutput(stdout)
        .setInput(stdin)
        .setLocalService(millServer)
        .setRemoteInterface(classOf[BuildClient]).
        traceMessages(new PrintWriter((os.pwd / "bsp.log").toIO))
        .setExecutorService(executor)
        .create()
      millServer.onConnectWithClient(launcher.getRemoteProxy)
      val listening = launcher.startListening()
      millServer.cancelator = () => listening.cancel(true)
      val voidFuture = listening.get()
    } catch {
      case _: CancellationException => System.err.println("The mill server was shut down.")
      case e: Exception =>
        System.err.println("An exception occured while connecting to the client.")
        System.err.println("Cause: " + e.getCause)
        System.err.println("Message: " + e.getMessage)
        System.err.println("Exception class: " + e.getClass)
        System.err.println("Stack Trace: " + e.getStackTrace)
    } finally {
      System.err.println("Shutting down executor")
      executor.shutdown()
    }
  }
}
