package magenta.input.resolver

import magenta.deployment_type.Param
import magenta.fixtures._
import magenta.input.{ConfigError, Deployment}
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import play.api.libs.json.JsNumber

class DeploymentTypeResolverTest extends FlatSpec with Matchers with EitherValues {
  val deployment = Deployment("bob", "stub-package-type", List("stack"), List("eu-west-1"), actions=None, "bob", "bob", Nil, Map.empty)
  val deploymentTypes = List(stubDeploymentType(Seq("upload", "deploy")))

  "validateDeploymentType" should "fail on invalid deployment type" in {
    val deploymentWithInvalidType = deployment.copy(`type` = "invalidType")
    val configError = DeploymentTypeResolver.validateDeploymentType(deploymentWithInvalidType, deploymentTypes).left.value
    configError.context shouldBe "bob"
    configError.message should include(s"Unknown type invalidType")
  }

  it should "fail if explicitly given empty actions" in {
    val deploymentWithNoActions = deployment.copy(actions = Some(Nil))
    val configError = DeploymentTypeResolver.validateDeploymentType(deploymentWithNoActions, deploymentTypes).left.value
    configError.context shouldBe "bob"
    configError.message should include(s"Either specify at least one action or omit the actions parameter")
  }

  it should "fail if given an invalid action" in {
    val deploymentWithInvalidAction = deployment.copy(actions = Some(List("invalidAction")))
    val configError = DeploymentTypeResolver.validateDeploymentType(deploymentWithInvalidAction, deploymentTypes).left.value
    configError.context shouldBe "bob"
    configError.message should include(s"Invalid action invalidAction for type stub-package-type")
  }

  it should "populate the deployment with default actions if no actions are provided" in {
    val validatedDeployment = DeploymentTypeResolver.validateDeploymentType(deployment, deploymentTypes).right.value
    validatedDeployment.actions shouldBe Some(List("upload", "deploy"))
  }

  it should "use specified actions if they are provided" in {
    val deploymentWithSpecifiedActions = deployment.copy(actions = Some(List("upload")))
    val validatedDeployment = DeploymentTypeResolver.validateDeploymentType(deploymentWithSpecifiedActions, deploymentTypes).right.value
    validatedDeployment.actions shouldBe Some(List("upload"))
  }

  it should "preserve other fields" in {
    val validatedDeployment = DeploymentTypeResolver.validateDeploymentType(deployment, deploymentTypes).right.value
    validatedDeployment should have(
      'name ("bob"),
      'type ("stub-package-type"),
      'stacks (List("stack")),
      'regions (List("eu-west-1")),
      'app ("bob"),
      'contentDirectory ("bob"),
      'dependencies (Nil),
      'parameters (Map.empty)
    )
  }

  it should "fail if given an invalid parameter" in {
    val deploymentWithParameters = deployment.copy(parameters = Map("param1" -> JsNumber(1234)))
    val configError = DeploymentTypeResolver.validateDeploymentType(deploymentWithParameters, deploymentTypes).left.value
    configError shouldBe ConfigError("bob", "Parameters provided but not used by stub-package-type deployments: param1")
  }

  it should "fail if a parameter with no default is not provided" in {
    val deploymentTypesWithParams = List(
      stubDeploymentType(
        Seq("upload", "deploy"),
        register => List(Param[String]("param1")(register))
      )
    )
    val configError = DeploymentTypeResolver.validateDeploymentType(deployment, deploymentTypesWithParams).left.value
    configError shouldBe ConfigError("bob", "Parameters required for stub-package-type deployments not provided: param1")
  }

  it should "succeed if a default is provided" in {
    val deploymentTypesWithParams = List(
      stubDeploymentType(
        Seq("upload", "deploy"),
        register => List(Param[String]("param1", defaultValue = Some("defaultValue"))(register))
      )
    )
    DeploymentTypeResolver.validateDeploymentType(deployment, deploymentTypesWithParams).right.value
  }

  it should "succeed if a default from package is provided" in {
    val deploymentTypesWithParams = List(
      stubDeploymentType(
        Seq("upload", "deploy"),
        register => List(Param[String]("param1", defaultValueFromPackage = Some(_.name))(register))
      )
    )
    DeploymentTypeResolver.validateDeploymentType(deployment, deploymentTypesWithParams).right.value
  }
}
