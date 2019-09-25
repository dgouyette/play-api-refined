package bodyParsers
import akka.stream.Materializer
import play.api.http.{DefaultHttpErrorHandler, ParserConfiguration}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.{JsError, Reads}
import play.api.mvc.{BodyParser, PlayBodyParsers, Results}
import scala.concurrent.{ExecutionContext, Future}
import javax.inject._
import play.api.http.HttpErrorHandler
import refined.JsonSchema
import play.api.libs.json.Json
import play.api.libs.json.JsObject

class DefaultBodyParsers @Inject()(
   val materializer: Materializer,
   val config: ParserConfiguration,
   val temporaryFileCreator: TemporaryFileCreator,
   val errorHandler : HttpErrorHandler
  )  extends PlayBodyParsers {

  def jsonRefined[A](implicit reader: Reads[A]) = {

    BodyParser("json reader") { request =>
      implicit val ec = ExecutionContext.global
      json(request) mapFuture {
        case Left(simpleResult) =>
          Future.successful(Left(simpleResult))
        case Right(jsValue) =>
          jsValue.validate(reader) map { a =>
            Future.successful(Right(a))
          } recoverTotal { jsError =>
            errorHandler match {
              case _ : DefaultHttpErrorHandler => {
                val msg =s"${request.method}  on ${request.uri}"+ JsError.toFlatForm(jsError).mkString("\n")
                Future.successful(Left(Results.BadRequest(msg)))
              }
              case _ => {
                val error = JsError.toJson(jsError) ++ Json.obj("_schema" -> Json.obj("todo"-> true))
                Future.successful(Left(Results.BadRequest(error)))
              
              }
            }
          }
      }
    }
  }
}