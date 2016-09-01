package http.service

/**
  * Created by mma on 9/1/16.
  */

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import im.nasim.kamon.CacheActor
import org.slf4j.{LoggerFactory => SLFLoggerFactory}
import spray.json._

import scala.util.{Failure, Success}

final class CommandHttpHandler(implicit system: ActorSystem) extends BaseHttpHandler {
  implicit val timeout = Timeout(30.seconds)
  val cache = system.actorSelection("/user/cache")
  val log = SLFLoggerFactory getLogger this.getClass


  override def routes: Route =
    defaultVersion {
      path("command") {
        (post & entity(as[Command])) { cm ⇒
          onComplete(commandHandler(cm)) {
            case Success(Right(result)) ⇒
              complete(HttpResponse(
                status = OK,
                entity = result.toJson.prettyPrint
              ))
            case Success(Left(errors)) ⇒
              complete(HttpResponse(
                status = NotAcceptable,
                entity = errors.toJson.prettyPrint
              ))
            case Failure(e) ⇒
              log.warn(e.getMessage,e)
              complete(HttpResponse(InternalServerError))
          }
        }

      }
    }

  import akka.pattern.ask
  import ExecutionContext.Implicits.global

  private def commandHandler(command: Command): Future[Either[Errors, String]] = {

    command.name match {
      case "interval" =>
        cache ! CacheActor.Interval(command.start , command.end)
        FastFuture.successful[Either[Errors, String]](Right("Accepted"))
    }


  }
}
