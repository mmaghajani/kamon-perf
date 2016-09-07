package im.nasim.kamon.perf.http.service

/**
  * Created by mma on 9/1/16.
  */

import java.util.Calendar

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture
import akka.io.Tcp.Write

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import im.nasim.kamon.perf.reporter.{CacheActor, Statistic}
import org.slf4j.{LoggerFactory => SLFLoggerFactory}
import spray.json._

import scala.util.{Failure, Success}
import im.nasim.kamon.perf.http.HttpApiHelpers.defaultVersion

final class CommandHttpHandler(implicit system: ActorSystem) extends BaseHttpHandler {
  implicit val timeout = Timeout(300.seconds)
  val cache = system.actorSelection("/user/cache")
  val log = SLFLoggerFactory getLogger this.getClass


  implicit val myTraitFormat = new JsonFormat[(Calendar, Statistic)] {
    override def read(json: JsValue): (Calendar, Statistic) = json.asJsObject.getFields("value") match {
      //case Seq(JsString("TEST")) ⇒ MyCaseClass
      case _ ⇒ throw new DeserializationException(s"$json is not a valid extension of my trait")
    }

    override def write(myTrait: (Calendar, Statistic)): JsValue = {
      JsObject(myTrait._1.getTime.formatted("yyyy-dd-mm") -> JsString(myTrait._2.toString))
    }
  }

  override def routes: Route =
    defaultVersion {
      path("command") {
        (post & entity(as[Command])) { cm ⇒
          onComplete(commandHandler(cm)) {
            case Success(result) ⇒
              complete(HttpResponse(
                status = OK,
                entity = result.toJson.prettyPrint
              ))
            case Failure(e) ⇒
              log.warn(e.getMessage, e)
              complete(HttpResponse(InternalServerError))
          }
        }

      }
    }

  import akka.pattern.ask
  import ExecutionContext.Implicits.global

  private def commandHandler(command: Command): Future[Seq[(Calendar, Statistic)]] = {

    command.name match {
      case "interval" =>

        (cache ? CacheActor.Interval(command.start, command.end))
          .
          asInstanceOf[Future[Seq[(Calendar, Statistic)]]]
    }


  }
}




