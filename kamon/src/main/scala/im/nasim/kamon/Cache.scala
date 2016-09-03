package im.nasim.kamon

import java.util.Calendar

import akka.actor.{Actor, ActorLogging, Props}
import com.github.benmanes.caffeine.cache.Cache
import im.nasim.kamon.CacheActor._

/**
  * Created by mma on 9/1/16.
  */
class CacheActor extends Actor with ActorLogging{
  var cache = CacheHelpers.createCache[Calendar , Statistic](1000)

  private def isGreaterThan(cal1 : Calendar , cal2 : Calendar) : Boolean = {
   if( cal1.getTimeInMillis >= cal2.getTimeInMillis )
     true
    else
     false
  }

  override def receive: Receive = {
    case Put( key : Calendar , value : Statistic ) => cache.put(key , value)
    case Get( key : Calendar ) =>
      val statistic = Option{cache.getIfPresent(key)}
      sender() ! GetResponse(statistic)
    case Interval( start : Calendar , end : Calendar) =>
      val replyTo = sender()
      val records : Seq[(Calendar , Statistic)]  =
      for( (calendar , statistic) <- cache.asMap()
      if isGreaterThan(calendar , start) && !isGreaterThan(calendar , end))yield(calendar , statistic)
      replyTo ! records

    case GetAll => sender() ! GetAllResponse( Some(cache))
  }
}

object CacheActor {
  def props : Props = Props(classOf[CacheActor])

  case class Get( key : Calendar )

  case object GetAll

  case class GetResponse( value : Option[Statistic])

  case class GetAllResponse( value : Option[Cache[Calendar , Statistic]])

  case class Put( key : Calendar , value : Statistic )

  case class Interval( start : Calendar , end : Calendar)

  case class IntervalResponse( value : Seq[(Calendar , Statistic)])
}
