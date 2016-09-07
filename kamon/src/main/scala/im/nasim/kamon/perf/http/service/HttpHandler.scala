package im.nasim.kamon.perf.http.service

/**
  * Created by mma on 9/1/16.
  */

import akka.http.scaladsl.server.Route
import im.nasim.kamon.perf.http.HttpApiHelpers

trait HttpHandler extends HttpApiHelpers {
  def routes: Route
}
