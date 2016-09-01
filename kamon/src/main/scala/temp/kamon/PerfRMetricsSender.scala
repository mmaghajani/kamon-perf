package temp.kamon

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.{IO, Udp}
import java.net.InetSocketAddress

import akka.util.ByteString
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

import kamon.metric.instrument.{Counter, Histogram}

class PerfRMetricsSender(perfRHost: String, perfRPort: Int, maxPacketSizeInBytes: Long, metricKeyGenerator: MetricKeyGenerator)
  extends Actor with UdpExtensionProvider {
  import context.system

  val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)

  udpExtension ! Udp.SimpleSender

  def newSocketAddress = new InetSocketAddress(perfRHost, perfRPort)

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case tick: TickMetricSnapshot ⇒ writeMetricsToRemote(tick, udpSender)
  }

  def writeMetricsToRemote(tick: TickMetricSnapshot, udpSender: ActorRef): Unit = {
    val packetBuilder = new MetricDataPacketBuilder(maxPacketSizeInBytes, udpSender, newSocketAddress)

    for (
      (entity, snapshot) ← tick.metrics;
      (metricKey, metricSnapshot) ← snapshot.metrics
    ) {

      val key = metricKeyGenerator.generateKey(entity, metricKey)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          hs.recordsIterator.foreach { record ⇒
            packetBuilder.appendMeasurement(key, encodeStatsDTimer(record.level, record.count))
          }

        case cs: Counter.Snapshot ⇒
          packetBuilder.appendMeasurement(key, encodeStatsDCounter(cs.count))
      }
    }

    packetBuilder.flush()
  }

  def encodeStatsDTimer(level: Long, count: Long): String = {
    val samplingRate: Double = 1D / count
    level.toString + "|ms" + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")
  }

  def encodeStatsDCounter(count: Long): String = count.toString + "|c"
}

object PerfRMetricsSender {
  def props(perfRHost: String, perfRPort: Int, maxPacketSize: Long, metricKeyGenerator: MetricKeyGenerator): Props =
    Props(new PerfRMetricsSender(perfRHost, perfRPort, maxPacketSize, metricKeyGenerator))
}

trait UdpExtensionProvider {
  def udpExtension(implicit system: ActorSystem): ActorRef = IO(Udp)
}

class MetricDataPacketBuilder(maxPacketSizeInBytes: Long, udpSender: ActorRef, remote: InetSocketAddress) {
  val metricSeparator = "\n"
  val measurementSeparator = ":"

  var lastKey = ""
  var buffer = new StringBuilder()

  def appendMeasurement(key: String, measurementData: String): Unit = {
    if (key == lastKey) {
      val dataWithoutKey = measurementSeparator + measurementData
      if (fitsOnBuffer(dataWithoutKey))
        buffer.append(dataWithoutKey)
      else {
        flushToUDP(buffer.toString())
        buffer.clear()
        buffer.append(key).append(dataWithoutKey)
      }
    } else {
      lastKey = key
      val dataWithoutSeparator = key + measurementSeparator + measurementData
      if (fitsOnBuffer(metricSeparator + dataWithoutSeparator)) {
        val mSeparator = if (buffer.length > 0) metricSeparator else ""
        buffer.append(mSeparator).append(dataWithoutSeparator)
      } else {
        flushToUDP(buffer.toString())
        buffer.clear()
        buffer.append(dataWithoutSeparator)
      }
    }
  }

  def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

  private def flushToUDP(data: String): Unit = udpSender ! Udp.Send(ByteString(data), remote)

  def flush(): Unit = {
    flushToUDP(buffer.toString)
    buffer.clear()
  }
}