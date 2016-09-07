package im.nasim.kamon.perf.http

/**
  * Created by mma on 9/1/16.
  */
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import im.nasim.kamon.perf.http.service._
import scala.concurrent.duration._
import scala.util.{Failure, Success}


final class HttpApi(_system: ActorSystem) extends Extension {
  implicit val system = _system
  implicit val ec = system.dispatcher
  implicit val mat = ActorMaterializer()

  def start(): Unit =
    HttpApiFrontend.start(system.settings.config)

}

object HttpApi extends ExtensionId[HttpApi] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): HttpApi = new HttpApi(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpApi
}

private object HttpApiFrontend {

  private val IdleTimeout = 15.minutes

  def start(serverConfig: Config)(implicit system: ActorSystem): Unit = {
    HttpApiConfig.load(serverConfig.getConfig("http")) match {
      case Success(apiConfig) ⇒
        start(apiConfig)
      case Failure(e) ⇒
        throw e
    }
  }

  def start(config: HttpApiConfig)(implicit system: ActorSystem): Unit = {
    implicit val mat = ActorMaterializer()


    val routes =
        new CommandHttpHandler().routes


    val defaultSettings = ServerSettings(system)

    Http().bindAndHandle(routes,
      config.interface,
      config.port,
      connectionContext = Http().defaultServerHttpContext,
      settings = defaultSettings.withTimeouts(defaultSettings.timeouts.withIdleTimeout(IdleTimeout))
    )
  }
}
