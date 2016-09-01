package http.service

/**
  * Created by mma on 9/1/16.
  */

import java.util.Calendar

import spray.json.DefaultJsonProtocol
case class Status(uptime: String)



case class Command(name: String, parameter: String, start : Calendar , end : Calendar)

case class Errors(message: String)

trait Protocol extends DefaultJsonProtocol {
  implicit val errorFormatter = jsonFormat1(Errors.apply)
  implicit val statusFormatter = jsonFormat1(Status.apply)
  implicit val commandFormatter = jsonFormat4(Command.apply)
}

