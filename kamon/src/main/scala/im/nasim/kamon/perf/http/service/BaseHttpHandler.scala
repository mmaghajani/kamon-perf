package im.nasim.kamon.perf.http.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
/**
  * Created by mma on 9/1/16.
  */


trait BaseHttpHandler extends HttpHandler with Protocol  with SprayJsonSupport
