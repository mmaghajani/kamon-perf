package http

/**
  * Created by mma on 9/1/16.
  */

import scala.util.Try
import com.typesafe.config.Config

case class HttpApiConfig(interface: String, port: Int)

object HttpApiConfig {
  def load(config: Config): Try[HttpApiConfig] =
    Try{HttpApiConfig(config.getString("interface") , config.getInt("port"))}

  def load: Try[HttpApiConfig] = load(NasimConfig.load().getConfig("http"))
}


import java.util.concurrent.TimeUnit


import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.duration._

object NasimConfig {

  def load(defaults: Config = ConfigFactory.empty()): Config = {
    val mainConfig = ConfigFactory.load()

    val config = defaults.withFallback(ConfigFactory.parseString(
      s"""
         |akka {
         |  actor {
         |    provider = "akka.cluster.ClusterActorRefProvider"
         |  }
         |  remote {
         |    log-remote-lifecycle-events = off
         |    netty.tcp {
         |      hostname = "127.0.0.1"
         |      port = 0
         |    }
         |  }
         |  cluster {
         |    auto-down-unreachable-after = 10s
         |  }
         |  http {
         |    server.remote-address-header: on
         |  }
         |}
      """.stripMargin
    ))
      .withFallback(mainConfig)
      .withFallback(ConfigFactory.parseResources("runtime.conf"))
      .resolve()

    config
  }

  val defaultTimeout: FiniteDuration = NasimConfig.load().getDuration("common.default-timeout", TimeUnit.MILLISECONDS).millis
}


