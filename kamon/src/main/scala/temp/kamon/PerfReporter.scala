package temp.kamon

import akka.actor.{ActorRef, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.Logging
import com.typesafe.config.Config
import kamon.Kamon
import kamon.util.ConfigTools.Syntax
import kamon.metric.TickMetricSnapshotBuffer
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object PerfReporter extends ExtensionId[PerfReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = PerfReporter
  override def createExtension(system: ExtendedActorSystem): PerfReporterExtension = new PerfReporterExtension(system)
}

class PerfReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val as = system

  val log = Logging(system, classOf[PerfReporterExtension])
  log.info("Starting the Kamon(PerfReporter) extension")

  private val config = system.settings.config
  private val perfConfig = config.getConfig("kamon.perf")
  val metricsExtension = Kamon.metrics

  val tickInterval = metricsExtension.settings.tickInterval
  val flushInterval = perfConfig.getFiniteDuration("flush-interval")
  val maxPacketSizeInBytes = perfConfig.getBytes("max-packet-size")
  val keyGeneratorFQCN = perfConfig.getString("metric-key-generator")

  val statsDMetricsListener = buildMetricsListener(tickInterval, flushInterval, keyGeneratorFQCN, config)

  val subscriptions = perfConfig.getConfig("subscriptions")
  subscriptions.firstLevelKeys.map { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      metricsExtension.subscribe(subscriptionCategory, pattern, statsDMetricsListener, permanently = true)
    }
  }

  def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration, keyGeneratorFQCN: String, config: Config): ActorRef = {
    assert(flushInterval >= tickInterval, "PerfR flush-interval needs to be equal or greater to the tick-interval")
    val keyGenerator = system.dynamicAccess.createInstanceFor[MetricKeyGenerator](keyGeneratorFQCN, (classOf[Config], config) :: Nil).get

    val metricsSender = system.actorOf(PerfRMetricsSender.props(
      perfConfig.getString("hostname"),
      perfConfig.getInt("port"),
      maxPacketSizeInBytes,
      keyGenerator), "perfr-metrics-sender")

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, metricsSender), "perfr-metrics-buffer")
    }
  }
}