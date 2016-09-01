package kamontest

import MessageGenerator._
import RandomNumberActor._
import akka.actor.{ActorSystem, Props}
import http.im.nasim.perf.http.HttpApi
import im.nasim.kamon.CacheActor.{GetAll, GetAllResponse}
import kamon.Kamon

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {

  Kamon.start()

  val someHistogram = Kamon.metrics.histogram("some-histogram")
  val someCounter = Kamon.metrics.counter("some-counter")

  someHistogram.record(42)
  someHistogram.record(50)
  someCounter.increment()

  val system = ActorSystem("application")

  val cache = system.actorSelection("/user/cache")

  val numberGenerator = system.actorOf(Props[RandomNumberActor], "numbers")

  val generator = system.actorOf(Props[MessageGeneratorActor], "artifical")

  HttpApi(system).start() ;

  generator ! ConstantLoad(Schedule(numberGenerator, GenerateNumber, 5000))
  generator ! ConstantLoad(Schedule(numberGenerator, GenerateSecureNumber, 1000))
  generator ! Peak(Schedule(numberGenerator, GenerateNumber, 100000))
  generator ! Peak(Schedule(numberGenerator, GenerateSecureNumber, 25000))

  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._

  implicit val timeOut: Timeout = Timeout(30 seconds)
  (cache ? GetAll).mapTo[GetAllResponse].map(result => {
    println(result.value)
  })

}
