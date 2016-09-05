package magenta

import java.util.UUID

import com.amazonaws.services.s3.AmazonS3Client
import magenta.fixtures.{StubTask, _}
import magenta.tasks.{Task, TaskGraph}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Matchers.any

import scala.collection.mutable.Buffer

class DeployContextTest extends FlatSpec with Matchers with MockitoSugar {
  val artifactClient = mock[AmazonS3Client]

  it should ("resolve a set of tasks") in {
    val reporter = DeployReporter.rootReporterFor(UUID.randomUUID(), fixtures.parameters())
    val parameters = DeployParameters(Deployer("tester"), Build("project","1"), CODE, oneRecipeName)
    val context = DeployContext(UUID.randomUUID(), parameters, project(baseRecipe), lookupSingleHost, reporter, artifactClient)
    context.tasks.toTaskList() should be(List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on the_host", lookupSingleHost.hosts.all.headOption)
    ))
  }

  it should ("send a Info and TaskList message when resolving tasks") in {
    val parameters = DeployParameters(Deployer("tester1"), Build("project","1"), CODE, oneRecipeName)
    val reporter = DeployReporter.startDeployContext(DeployReporter.rootReporterFor(UUID.randomUUID(), parameters))

    val messages = Buffer[Message]()
    DeployReporter.messages.filter(_.stack.deployParameters == Some(parameters)).subscribe(messages += _.stack.top)

    val context = DeployContext(reporter.messageContext.deployId, parameters, project(baseRecipe), lookupSingleHost, reporter, artifactClient)

    messages.filter(_.getClass == classOf[Info]) should have size (1)
    messages.filter(_.getClass == classOf[TaskList]) should have size (1)
  }

  it should ("execute the task") in {
    val parameters = DeployParameters(Deployer("tester"), Build("prooecjt","1"), CODE, oneRecipeName)
    val reporter = DeployReporter.rootReporterFor(UUID.randomUUID(), parameters)
    val context = DeployContext(reporter.messageContext.deployId, parameters, project(baseMockRecipe), lookupSingleHost, reporter, artifactClient)
    context.execute()
    val task = context.tasks.toTaskList().head

    verify(task, times(1)).execute(any[DeployReporter])
  }

  it should ("send taskStart and taskFinish messages for each task") in {
    val parameters = DeployParameters(Deployer("tester2"), Build("project","1"), CODE, oneRecipeName)

    val start = Buffer[Message]()
    val finished = Buffer[Message]()
    DeployReporter.messages.filter(_.stack.deployParameters == Some(parameters)).subscribe(wrapper =>
      wrapper.stack.top match {
        case FinishContext(finishMessage) => finished += finishMessage
        case StartContext(startMessage) => start += startMessage
        case _ =>
      }
    )

    val reporter = DeployReporter.startDeployContext(DeployReporter.rootReporterFor(UUID.randomUUID(), parameters))
    val context = DeployContext(reporter.messageContext.deployId, parameters, project(baseRecipe), lookupSingleHost, reporter, artifactClient)

    context.execute()

    DeployReporter.finishContext(reporter)

    start.filter(_.getClass == classOf[Deploy]) should have size (1)
    start.filter(_.getClass == classOf[TaskRun]) should have size (2)
    finished.filter(_.getClass == classOf[Deploy]) should have size (1)
    finished.filter(_.getClass == classOf[TaskRun]) should have size (2)
  }

  it should ("bookend the messages with startdeploy and finishdeploy messages") in {
    val parameters = DeployParameters(Deployer("tester3"), Build("Project","1"), CODE, oneRecipeName)

    val start = Buffer[Message]()
    val finished = Buffer[Message]()
    DeployReporter.messages.filter(_.stack.deployParameters == Some(parameters)).subscribe(wrapper =>
      wrapper.stack.top match {
        case FinishContext(finishMessage) => finished += finishMessage
        case StartContext(startMessage) => start += startMessage
        case _ =>
      }
    )

    val reporter = DeployReporter.startDeployContext(DeployReporter.rootReporterFor(UUID.randomUUID(), parameters))
    val context = DeployContext(reporter.messageContext.deployId, parameters, project(baseRecipe), lookupSingleHost, reporter, artifactClient)

    context.execute()

    DeployReporter.finishContext(reporter)

    start.head.getClass should be(classOf[Deploy])
    finished.last.getClass should be(classOf[Deploy])
  }


  val CODE = Stage("CODE")

  case class MockStubPerHostAction(description: String, apps: Seq[App]) extends Action {
    def resolve(resources: DeploymentResources, target: DeployTarget) = {
      val task = mock[Task]
      when(task.taskHost).thenReturn(Some(resources.lookup.hosts.all.head))
      task :: Nil
    }
  }

  case class MockStubPerAppAction(description: String, apps: Seq[App]) extends Action {
    def resolve(resources: DeploymentResources, target: DeployTarget) = {
      val task = mock[Task]
      when(task.taskHost).thenReturn(None)
      task :: Nil
    }
  }

  val app1 = App("the_role")
  val app2 = App("the_2nd_role")

  val oneRecipeName = RecipeName("one")

  val basePackageType = stubPackageType(Seq("init_action_one"), Seq("action_one"))

  val baseRecipe = Recipe("one",
    actionsBeforeApp = basePackageType.mkAction("init_action_one")(stubPackage) :: Nil,
    actionsPerHost = basePackageType.mkAction("action_one")(stubPackage) :: Nil,
    dependsOn = Nil)

  val baseMockRecipe = Recipe("one",
    actionsBeforeApp = MockStubPerAppAction("init_action_one", Seq(app1)) :: Nil,
    actionsPerHost = MockStubPerHostAction("action_one", Seq(app1)) :: Nil,
    dependsOn = Nil)

  def project(recipes: Recipe*) = Project(Map.empty, recipes.map(r => r.name -> r).toMap)

}