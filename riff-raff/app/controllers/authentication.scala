package controllers

import cats.data.EitherT
import com.gu.googleauth._
import com.mongodb.casbah.commons.MongoDBObject
import conf.Configuration.auth
import conf._
import deployment.{DeployFilter, Deployments}
import org.joda.time.DateTime
import persistence.{MongoFormat, MongoSerialisable, Persistence}
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.mvc._
import rx.functions.Actions

import scala.concurrent.{ExecutionContext, Future}

class ApiRequest[A](val apiKey: ApiKey, request: Request[A]) extends WrappedRequest[A](request) {
  lazy val fullName = s"API:${apiKey.application}"
}

case class AuthorisationRecord(email: String, approvedBy: String, approvedDate: DateTime)
object AuthorisationRecord extends MongoSerialisable[AuthorisationRecord] {
  implicit val authFormat:MongoFormat[AuthorisationRecord] = new AuthMongoFormat
  private class AuthMongoFormat extends MongoFormat[AuthorisationRecord] {
    def toDBO(a: AuthorisationRecord) = MongoDBObject("_id" -> a.email, "approvedBy" -> a.approvedBy, "approvedDate" -> a.approvedDate)
    def fromDBO(dbo: MongoDBObject) = Some(AuthorisationRecord(dbo.as[String]("_id"), dbo.as[String]("approvedBy"), dbo.as[DateTime]("approvedDate")))
  }
}

trait AuthorisationValidator {
  def emailDomainWhitelist: List[String]
  def emailWhitelistEnabled: Boolean
  def emailWhitelistContains(email:String): Boolean
  def isAuthorised(id: UserIdentity) = authorisationError(id).isEmpty
  def authorisationError(id: UserIdentity): Option[String] = {
    if (!emailDomainWhitelist.isEmpty && !emailDomainWhitelist.contains(id.emailDomain)) {
      Some(s"The e-mail address domain you used to login to Riff-Raff (${id.email}) is not in the configured whitelist.  Please try again with another account or contact the Riff-Raff administrator.")
    } else if (emailWhitelistEnabled && !emailWhitelistContains(id.email)) {
      Some(s"The e-mail address you used to login to Riff-Raff (${id.email}) is not authorised.  Please try again with another account, ask a colleague to add your address or contact the Riff-Raff administrator.")
    } else {
      None
    }
  }
}

class Login(deployments: Deployments, val controllerComponents: ControllerComponents, val authAction: AuthAction[AnyContent])
  (implicit val wsClient: WSClient, val executionContext: ExecutionContext)
  extends BaseController with Logging with LoginSupport with I18nSupport {

  val validator = new AuthorisationValidator {
    def emailDomainWhitelist = auth.domains
    def emailWhitelistEnabled = auth.whitelist.useDatabase || auth.whitelist.addresses.nonEmpty
    def emailWhitelistContains(email: String) = {
      val lowerCaseEmail = email.toLowerCase
      auth.whitelist.addresses.contains(lowerCaseEmail) ||
        (auth.whitelist.useDatabase && Persistence.store.getAuthorisation(lowerCaseEmail).isDefined)
    }
  }

  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.auth.login(request, error))
  }

  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    import cats.instances.future._
    (for {
      identity <- checkIdentity()
      _ <- EitherT.fromEither[Future] {
        if (validator.isAuthorised(identity)) Right(())
        else Left(redirectWithError(
          failureRedirectTarget,  validator.authorisationError(identity).getOrElse("Unknown error"), authConfig.antiForgeryKey, request.session))
      }
    } yield {
      setupSessionWhenSuccessful(identity)
    }).merge
  }

  def logout = Action { implicit request =>
    Redirect("/").withNewSession
  }

  def profile = authAction { request =>
    val records = deployments.getDeploys(Some(DeployFilter(deployer=Some(request.user.fullName)))).reverse
    Ok(views.html.auth.profile(request, records))
  }

  val authorisationForm = Form( "email" -> nonEmptyText )

  def authList = authAction { request =>
    Ok(views.html.auth.list(request, Persistence.store.getAuthorisationList.sortBy(_.email)))
  }

  def authForm = authAction { implicit request =>
    Ok(views.html.auth.form(authorisationForm))
  }

  def authSave = authAction { implicit request =>
    authorisationForm.bindFromRequest().fold(
      errors => BadRequest(views.html.auth.form(errors)),
      email => {
        val auth = AuthorisationRecord(email.toLowerCase, request.user.fullName, new DateTime())
        Persistence.store.setAuthorisation(auth)
        Redirect(routes.Login.authList())
      }
    )
  }

  def authDelete = authAction { implicit request =>
    authorisationForm.bindFromRequest().fold( _ => {}, email => {
      log.info(s"${request.user.fullName} deleted authorisation for $email")
      Persistence.store.deleteAuthorisation(email)
    } )
    Redirect(routes.Login.authList())
  }

  override def authConfig: GoogleAuthConfig = auth.googleAuthConfig

  override val failureRedirectTarget: Call = routes.Application.index()
  override val defaultRedirectTarget: Call = routes.Login.login()
}
