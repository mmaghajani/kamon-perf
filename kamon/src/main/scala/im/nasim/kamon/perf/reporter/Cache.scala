package im.nasim.kamon.perf.reporter

import java.text.SimpleDateFormat
import java.util.{Calendar, Locale}
import scala.collection.JavaConverters._
import akka.actor.{Actor, ActorLogging, Props}
import com.github.benmanes.caffeine.cache.Cache
import im.nasim.kamon.perf.reporter.CacheActor._

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
    case Interval( start : String , end : String) =>
      val replyTo = sender()
      val startDate : Calendar = Calendar.getInstance()
      val endDate : Calendar = Calendar.getInstance()
      val sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy")
      startDate.setTime(sdf.parse(start))// all done
      log.info("craeted : " + startDate.getTime.toString)
      endDate.setTime(sdf.parse(start))// all done
      val records = cache.asMap().asScala.filterKeys(a => {isGreaterThan( a , startDate) && (!isGreaterThan(a , endDate)) })
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

  case class Interval( startDate : String , endDate : String)

  case class IntervalResponse( value : Seq[(Calendar , Statistic)])
}
