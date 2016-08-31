package im.nasim.kamon

import java.lang.management.ManagementFactory

import com.typesafe.config.Config
import kamon.metric.{Entity, MetricKey, SingleInstrumentEntityRecorder}


class SimpleMetricKeyGenerator(config: Config) extends MetricKeyGenerator {
  type Normalizer = String ⇒ String

  val configSettings = config.getConfig("kamon.perf.simple-metric-key-generator")
  val application = configSettings.getString("application")
  val includeHostname = configSettings.getBoolean("include-hostname")
  val hostnameOverride = configSettings.getString("hostname-override")
  val normalizer = createNormalizer(configSettings.getString("metric-name-normalization-strategy"))

  val normalizedHostname =
    if (hostnameOverride.equals("none")) normalizer(hostName)
    else normalizer(hostnameOverride)

  val baseName: String =
    if (includeHostname) s"$application.$normalizedHostname"
    else application

  def generateKey(entity: Entity, metricKey: MetricKey): String = entity.category match {
    case "trace-segment" ⇒
      s"${baseName}.trace.${normalizer(entity.tags("trace"))}.segments.${normalizer(entity.name)}.${metricKey.name}"

    case _ if SingleInstrumentEntityRecorder.AllCategories.contains(entity.category) ⇒
      s"${baseName}.${entity.category}.${normalizer(entity.name)}"

    case _ ⇒
      s"${baseName}.${entity.category}.${normalizer(entity.name)}.${metricKey.name}"

  }

  def hostName: String = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)

  def createNormalizer(strategy: String): Normalizer = strategy match {
    case "percent-encode" ⇒ PercentEncoder.encode
    case "normalize"      ⇒ (s: String) ⇒ s.replace(": ", "-").replace(" ", "_").replace("/", "_").replace(".", "_")
  }
}


