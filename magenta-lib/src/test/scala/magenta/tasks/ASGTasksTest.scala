package magenta.tasks

import com.amazonaws.services.autoscaling.model.{AutoScalingGroup, SetDesiredCapacityRequest}
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import magenta._
import org.mockito.Mockito._
import org.scalatest.{FlatSpec, Matchers}
import magenta.KeyRing
import magenta.Stage
import org.scalatest.mock.MockitoSugar
import java.io.File
import java.util.UUID

class ASGTasksTest extends FlatSpec with Matchers with MockitoSugar {
  implicit val fakeKeyRing = KeyRing(SystemUser(None))
  implicit val logger = DeployLogger.rootLoggerFor(UUID.randomUUID(), fixtures.parameters())

  it should "double the size of the autoscaling group" in {
    val asg = new AutoScalingGroup().withDesiredCapacity(3).withAutoScalingGroupName("test").withMaxSize(10)
    val asgClientMock = mock[AmazonAutoScalingClient]

    val p = DeploymentPackage("test", Seq(App("app")), Map.empty, "test", new File("/tmp/packages/webapp"))

    val task = new DoubleSize(p, Stage("PROD"), UnnamedStack) {
      override def client(implicit keyRing: KeyRing) = asgClientMock
      override def groupForAppAndStage(pkg: DeploymentPackage,  stage: Stage, stack: Stack)
                                      (implicit keyRing: KeyRing, logger: DeployLogger) = asg
    }

    task.execute(logger)

    verify(asgClientMock).setDesiredCapacity(
      new SetDesiredCapacityRequest().withAutoScalingGroupName("test").withDesiredCapacity(6)
    )
  }

  it should "fail if you do not have the capacity to deploy" in {
    val asg = new AutoScalingGroup().withAutoScalingGroupName("test")
      .withMinSize(1).withDesiredCapacity(1).withMaxSize(1)

    val asgClientMock = mock[AmazonAutoScalingClient]

    val p = DeploymentPackage("test", Seq(App("app")), Map.empty, "test", new File("/tmp/packages/webapp"))

    val task = new CheckGroupSize(p, Stage("PROD"), UnnamedStack) {
      override def client(implicit keyRing: KeyRing) = asgClientMock
      override def groupForAppAndStage(pkg: DeploymentPackage, stage: Stage, stack: Stack)
                                      (implicit keyRing: KeyRing, logger: DeployLogger) = asg
    }

    val thrown = intercept[FailException](task.execute(logger))

    thrown.getMessage should startWith ("Autoscaling group does not have the capacity")

  }
}
