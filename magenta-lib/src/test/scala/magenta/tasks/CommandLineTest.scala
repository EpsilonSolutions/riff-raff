package magenta
package tasks

import java.io.IOException
import java.util.UUID

import magenta.fixtures._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer


class CommandLineTest extends FlatSpec with Matchers {

  "CommandLine" should "return sensible description for simple commands" in {
    CommandLine(List("ls", "-l")).quoted should be ("ls -l")
  }

  it should "return quoted description for commands with string params with spaces" in {
    CommandLine(List("echo", "this needs to be quoted")).quoted should
      be ("echo \"this needs to be quoted\"")
  }

  it should "execute command and pipe progress results to Logger" in {
    val recordedMessages = new ListBuffer[List[Message]]()
    DeployLogger.messages.filter(_.stack.deployParameters == Some(parameters)).subscribe(recordedMessages += _.stack.messages)

    val logger = DeployLogger.startDeployContext(DeployLogger.rootLoggerFor(UUID.randomUUID(), parameters))
    val c = CommandLine(List("echo", "hello"))
    c.run(logger)
    DeployLogger.finishContext(logger)

    recordedMessages.toList should be (
      List(StartContext(Deploy(parameters))) ::
      List(StartContext(Info("$ echo hello")),Deploy(parameters)) ::
      List(CommandOutput("hello"),Info("$ echo hello"),Deploy(parameters)) ::
      List(Verbose("return value 0"),Info("$ echo hello"),Deploy(parameters)) ::
      List(FinishContext(Info("$ echo hello")), Info("$ echo hello"), Deploy(parameters)) ::
      List(FinishContext(Deploy(parameters)), Deploy(parameters)) ::
      Nil
    )
  }

  it should "throw when command is not found" in {
    a[FailException] should be thrownBy {
      val logger = DeployLogger.startDeployContext(DeployLogger.rootLoggerFor(UUID.randomUUID(), parameters))
      CommandLine(List("unknown_command")).run(logger)
    }
  }

  it should "throw when command returns non zero exit code" in {
    a[FailException] should be thrownBy {
      val logger = DeployLogger.startDeployContext(DeployLogger.rootLoggerFor(UUID.randomUUID(), parameters))
      CommandLine(List("false")).run(logger)
    }
  }

  val parameters = DeployParameters(Deployer("tester"), Build("Project","1"), CODE, RecipeName(baseRecipe.name))

}