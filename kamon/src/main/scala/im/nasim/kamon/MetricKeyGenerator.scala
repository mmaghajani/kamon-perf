package im.nasim.kamon

import kamon.metric.{Entity, MetricKey}

trait MetricKeyGenerator {
  def generateKey(entity: Entity, metricKey: MetricKey): String
}




