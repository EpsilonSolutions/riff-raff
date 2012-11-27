package controllers

import play.api.mvc.Controller
import magenta._
import collection.mutable.ArrayBuffer
import magenta.CommandOutput
import magenta.Info
import magenta.CommandError
import magenta.FinishContext
import magenta.DeployParameters
import magenta.StartContext
import magenta.TaskRun
import magenta.Verbose
import magenta.KeyRing
import magenta.MessageStack
import magenta.Deploy
import magenta.Deployer
import magenta.Stage
import magenta.Build
import deployment.{Task, DeployRecord}
import java.util.UUID
import tasks.Task
import play.api.data.Form
import play.api.data.Forms._
import org.joda.time.DateTime
import persistence.{DocumentConverter, DocumentStoreConverter, RecordConverter, Persistence}

case class SimpleDeployDetail(uuid: UUID, time: DateTime)

object Testing extends Controller with Logging {
  def reportTestPartial(verbose: Boolean) = NonAuthAction { implicit request =>
    val task1 = new Task {
      def execute(sshCredentials: KeyRing) {}
      def description = "Test task that does stuff, the first time"
      def verbose = "A particularly verbose task description that lists some stuff, innit"
    }
    val task2 = new Task {
      def execute(sshCredentials: KeyRing) {}
      def description = "Test task that does stuff"
      def verbose = "A particularly verbose task description that lists some stuff, innit"
    }
    val input = ArrayBuffer(
      MessageStack(List(
        StartContext(Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe()))))),
      MessageStack(List(
        Info("Downloading artifact"),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        Verbose("Downloading from http://teamcity.gudev.gnl:8111/guestAuth/repository/download/tools%3A%3Adeploy/131/artifacts.zip to /var/folders/ZO/ZOSa3fR3FsCiU3jxetWKQU+++TQ/-Tmp-/sbt_5489e15..."),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        Verbose("http: teamcity.gudev.gnl GET /guestAuth/repository/download/tools%3A%3Adeploy/131/artifacts.zip HTTP/1.1"),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        Verbose("""downloaded:
      /var/folders/ZO/ZOSa3fR3FsCiU3jxetWKQU+++TQ/-Tmp-/sbt_5489e15/deploy.json
    /var/folders/ZO/ZOSa3fR3FsCiU3jxetWKQU+++TQ/-Tmp-/sbt_5489e15/packages/riff-raff/riff-raff.jar"""),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        Info("Reading deploy.json"),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        StartContext(TaskRun(task1)),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        FinishContext(TaskRun(task1)),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        StartContext(TaskRun(task2)),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        StartContext(Info("$ command line action")),
        TaskRun(task2),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        CommandOutput("Some command output from command line action"),
        Info("$ command line action"),
        TaskRun(task2),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        CommandError("Some command error from command line action"),
        Info("$ command line action"),
        TaskRun(task2),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe())))),
      MessageStack(List(
        CommandOutput("Some more command output from command line action"),
        Info("$ command line action"),
        TaskRun(task2),
        Deploy(DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe()))))
    )

    val report = DeployRecord(new DateTime(), Task.Deploy, UUID.randomUUID(), DeployParameters(Deployer("Simon Hildrew"),Build("tools::deploy","131"),Stage("DEV"),DefaultRecipe()), input.toList)

    Ok(views.html.test.reportTest(request,report,verbose))
  }

  case class TestForm(project:String, action:String, hosts: List[String])

  lazy val testForm = Form[TestForm](
    mapping(
      "project" -> text,
      "action" -> nonEmptyText,
      "hosts" -> list(text)
    )(TestForm.apply)
      (TestForm.unapply)
  )

  def form =
    AuthAction { implicit request =>
      Ok(views.html.test.form(request, testForm))
    }

  def formPost =
    AuthAction { implicit request =>
      testForm.bindFromRequest().fold(
        errors => BadRequest(views.html.test.form(request,errors)),
        form => {
          log.info("Form post: %s" format form.toString)
          Redirect(routes.Testing.form)
        }
      )
    }



  def uuidList = AuthAction { implicit request =>
    val v1Set = Persistence.store.getDeployUUIDs.toSet
    val v2Set = Persistence.store.getDeployV2UUIDs.toSet
    val allDeploys = (v1Set ++ v2Set).toSeq.sortBy(_.time.getMillis).reverse
    Ok(views.html.test.uuidList(request, allDeploys, v1Set, v2Set))
  }

  case class UuidForm(uuid:String, action:String)

  lazy val uuidForm = Form[UuidForm](
    mapping(
      "uuid" -> text(36,36),
      "action" -> nonEmptyText
    )(UuidForm.apply)
      (UuidForm.unapply)
  )

  def actionUUID = AuthAction { implicit request =>
    uuidForm.bindFromRequest().fold(
      errors => Redirect(routes.Testing.uuidList()),
      form => {
        form.action match {
          case "deleteV1" => {
            log.info("Deleting deploy in V1 with UUID %s" format form.uuid)
            Persistence.store.deleteDeployLog(UUID.fromString(form.uuid))
            Redirect(routes.Testing.uuidList())
          }
          case "deleteV2" => {
            log.info("Deleting deploy in V2 with UUID %s" format form.uuid)
            Persistence.store.deleteDeployLogV2(UUID.fromString(form.uuid))
            Redirect(routes.Testing.uuidList())
          }
          case "migrate" => {
            log.info("Migrating deploy with UUID %s" format form.uuid)
            val deployRecord = Persistence.store.getDeploy(UUID.fromString(form.uuid))
            deployRecord.foreach{ deploy =>
              val conversion = RecordConverter(deploy)
              Persistence.store.writeDeploy(conversion.deployDocument)
              conversion.logDocuments.foreach(Persistence.store.writeLog(_))
            }
            Redirect(routes.Testing.uuidList())
          }
        }
      }
    )
  }

  def viewUUIDv2(uuid: String, verbose: Boolean) = AuthAction { implicit request =>
    val converter = DocumentStoreConverter(Persistence.store)
    val record = converter.getDeploy(UUID.fromString(uuid)).get
    record.taskType match {
      case Task.Deploy => Ok(views.html.deploy.log(request, record, verbose))
      case Task.Preview => Ok(views.html.deploy.preview(request,record,verbose))
    }
  }

  case class ComparisonCriteria(name: String, f: DeployRecord => Iterable[Any], diff: (DeployRecord, DeployRecord) => Iterable[Any] = (v1,v2) => Nil)

  case class Comparison(name: String, v1: Iterable[Any], v2: Iterable[Any], equality: Boolean, diff: Iterable[Any]) {
    lazy val cssClass = if (equality) "success" else "error"
  }

  def compareV1V2(uuid: String) = AuthAction { implicit request =>
    // v1
    val v1Record = Persistence.store.getDeploy(UUID.fromString(uuid)).get

    // v2
    val converter = DocumentStoreConverter(Persistence.store)
    val v2Record = converter.getDeploy(UUID.fromString(uuid)).get

    val comparisonCriteria: List[ComparisonCriteria] = List(
      ComparisonCriteria("UUID", r => List(r.uuid)),
      ComparisonCriteria("TaskType", r => List(r.taskType)),
      ComparisonCriteria("Parameters", r => List(r.parameters)),
      ComparisonCriteria("Number of stacks", r => List(r.messageStacks.size)),
      ComparisonCriteria("Number of unique stacks", r => List(r.messageStacks.toSet.size))
      //ComparisonCriteria("Stacks", _.messageStacks, (v1,v2) => (v1.messageStacks.toSet - v2.messageStacks.toSet))
    )

    val comparisons = comparisonCriteria.map { criteria =>
      val v1 = criteria.f(v1Record)
      val v2 = criteria.f(v2Record)
      Comparison(criteria.name, v1, v2, v1==v2, criteria.diff(v1Record,v2Record))
    }

    val stackComparisons = v1Record.messageStacks.zip(v2Record.messageStacks).map{ case (v1Stack, v2Stack) =>
      Comparison("Stack Item", List(v1Stack), List(v2Stack), v1Stack == v2Stack, Nil)
    }

    Ok(views.html.test.compare(request, comparisons ::: stackComparisons))
  }

  lazy val v1Record = Persistence.store.getDeploy(UUID.fromString("d10f036a-239d-4897-bc82-c79cb96611f5")).get
  lazy val v2Deploy = RecordConverter(v1Record).deployDocument
  lazy val v2Logs = RecordConverter(v1Record).logDocuments
  lazy val v2Record = {
    DocumentConverter(v2Deploy, v2Logs).deployRecord
  }

  lazy val checkMigrationIntegrity = {
    Persistence.store.getDeployUUIDs.map { uuid =>
      val v1Record = Persistence.store.getDeploy(uuid.uuid).get
      try {
        val converter = RecordConverter(v1Record)
        val v2Record = DocumentConverter(converter.deployDocument, converter.logDocuments).deployRecord
        uuid -> Some((v1Record == v2Record))
      } catch {
        case e:Exception => uuid -> None
      }
    }.toMap
  }

}
