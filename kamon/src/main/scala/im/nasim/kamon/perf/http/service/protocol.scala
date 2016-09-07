package im.nasim.kamon.perf.http.service

/**
  * Created by mma on 9/1/16.
  */

import java.util.{Calendar, Date}

import im.nasim.kamon.perf.reporter.Statistic
import spray.json._

case class Status(uptime: String)

case class Command(name: String, parameter: String, start : String , end : String)

case class Errors(message: String)

trait Protocol extends DefaultJsonProtocol {
  implicit val errorFormatter = jsonFormat1(Errors.apply)
  implicit val statusFormatter = jsonFormat1(Status.apply)
  implicit val commandFormatter = jsonFormat4(Command.apply)
}

