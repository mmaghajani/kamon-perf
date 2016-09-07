package im.nasim.kamon.perf.reporter

import java.util.Calendar

import akka.actor.{Actor, ActorLogging, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.event.Logging
import CacheActor.Put
import kamon.Kamon
//import im.nasim.kamon.PerfReporter.{PerfReporter, LogReporterExtension, LogReporterSubscriber}
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{Counter, Histogram}
import kamon.metric.{Entity, EntitySnapshot}

/**
  * Created by mma on 9/1/16.
  */
object PerfReporter extends ExtensionId[PerfReporterExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = PerfReporter
  override def createExtension(system: ExtendedActorSystem): PerfReporterExtension = new PerfReporterExtension(system)
}

class PerfReporterExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[PerfReporterExtension])
  log.info("Starting the Kamon(PerfReporter) extension")

  val subscriber = system.actorOf(Props[PerfReporterSubscriber], "kamon-perf-reporter")

  Kamon.metrics.subscribe("trace", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("akka-actor", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("akka-dispatcher", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("counter", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("histogram", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("min-max-counter", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("gauge", "**", subscriber, permanently = true)
  Kamon.metrics.subscribe("system-metric", "**", subscriber, permanently = true)
}

class PerfReporterSubscriber extends Actor with ActorLogging {
  import PerfReporterSubscriber.RichHistogramSnapshot

  val cache = context.system.actorOf(CacheActor.props , "cache")

  def receive = {
//    case tick: TickMetricSnapshot ⇒ printMetricSnapshot(tick)
    case tick : TickMetricSnapshot => storeMetric(tick)
  }


  def storeMetric(tick : TickMetricSnapshot ): Unit = {
    val histograms = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val counters = Map.newBuilder[String, Option[Counter.Snapshot]]
    val minMaxCounters = Map.newBuilder[String, Option[Histogram.Snapshot]]
    val gauges = Map.newBuilder[String, Option[Histogram.Snapshot]]

    tick.metrics foreach {
      case (entity, snapshot) if entity.category == "akka-actor"      ⇒ logActorMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "akka-dispatcher" ⇒ logDispatcherMetrics(entity, snapshot)
      case (entity, snapshot) if entity.category == "trace"           ⇒ logTraceMetrics(entity.name, snapshot)
      case (entity, snapshot) if entity.category == "histogram"       ⇒ histograms += (entity.name -> snapshot.histogram("histogram"))
      case (entity, snapshot) if entity.category == "counter"         ⇒ counters += (entity.name -> snapshot.counter("counter"))
      case (entity, snapshot) if entity.category == "min-max-counter" ⇒ minMaxCounters += (entity.name -> snapshot.minMaxCounter("min-max-counter"))
      case (entity, snapshot) if entity.category == "gauge"           ⇒ gauges += (entity.name -> snapshot.gauge("gauge"))
      case (entity, snapshot) if entity.category == "system-metric"   ⇒ logSystemMetrics(entity.name, snapshot) match {
        case Some(stat) => cache ! Put(Calendar.getInstance() , stat)
        case None => log.info("error occurred while storing data")
      }
      case ignoreEverythingElse                                       ⇒
    }

    logMetrics(histograms.result(), counters.result(), minMaxCounters.result(), gauges.result())
  }

  def logActorMetrics(name: String, actorSnapshot: EntitySnapshot): Unit = {
    for {
      processingTime ← actorSnapshot.histogram("processing-time")
      timeInMailbox ← actorSnapshot.histogram("time-in-mailbox")
      mailboxSize ← actorSnapshot.minMaxCounter("mailbox-size")
      errors ← actorSnapshot.counter("errors")
    } {

      log.info(
        """
          |+--------------------------------------------------------------------------------------------------+
          ||                                                                                                  |
          ||    Actor: %-83s    |
          ||                                                                                                  |
          ||   Processing Time (nanoseconds)      Time in Mailbox (nanoseconds)         Mailbox Size          |
          ||    Msg Count: %-12s               Msg Count: %-12s             Min: %-8s       |
          ||          Min: %-12s                     Min: %-12s            Avg.: %-8s       |
          ||    50th Perc: %-12s               50th Perc: %-12s             Max: %-8s       |
          ||    90th Perc: %-12s               90th Perc: %-12s                                 |
          ||    95th Perc: %-12s               95th Perc: %-12s                                 |
          ||    99th Perc: %-12s               99th Perc: %-12s           Error Count: %-6s   |
          ||  99.9th Perc: %-12s             99.9th Perc: %-12s                                 |
          ||          Max: %-12s                     Max: %-12s                                 |
          ||                                                                                                  |
          |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.format(
          name,
          processingTime.numberOfMeasurements, timeInMailbox.numberOfMeasurements, mailboxSize.min,
          processingTime.min, timeInMailbox.min, mailboxSize.average,
          processingTime.percentile(50.0D), timeInMailbox.percentile(50.0D), mailboxSize.max,
          processingTime.percentile(90.0D), timeInMailbox.percentile(90.0D),
          processingTime.percentile(95.0D), timeInMailbox.percentile(95.0D),
          processingTime.percentile(99.0D), timeInMailbox.percentile(99.0D), errors.count,
          processingTime.percentile(99.9D), timeInMailbox.percentile(99.9D),
          processingTime.max, timeInMailbox.max))
    }

  }

  def logDispatcherMetrics(entity: Entity, snapshot: EntitySnapshot): Unit = entity.tags.get("dispatcher-type") match {
    case Some("fork-join-pool")       ⇒ logForkJoinPool(entity.name, snapshot)
    case Some("thread-pool-executor") ⇒ logThreadPoolExecutor(entity.name, snapshot)
    case ignoreOthers                 ⇒
  }

  def logForkJoinPool(name: String, forkJoinMetrics: EntitySnapshot): Unit = {
    for {
      paralellism ← forkJoinMetrics.minMaxCounter("parallelism")
      poolSize ← forkJoinMetrics.gauge("pool-size")
      activeThreads ← forkJoinMetrics.gauge("active-threads")
      runningThreads ← forkJoinMetrics.gauge("running-threads")
      queuedTaskCount ← forkJoinMetrics.gauge("queued-task-count")

    } {
      log.info(
        """
          |+--------------------------------------------------------------------------------------------------+
          ||  Fork-Join-Pool                                                                                  |
          ||                                                                                                  |
          ||  Dispatcher: %-83s |
          ||                                                                                                  |
          ||  Paralellism: %-4s                                                                               |
          ||                                                                                                  |
          ||                 Pool Size       Active Threads     Running Threads     Queue Task Count          |
          ||      Min           %-4s              %-4s                %-4s                %-4s                |
          ||      Avg           %-4s              %-4s                %-4s                %-4s                |
          ||      Max           %-4s              %-4s                %-4s                %-4s                |
          ||                                                                                                  |
          |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.format(name,
          paralellism.max, poolSize.min, activeThreads.min, runningThreads.min, queuedTaskCount.min,
          poolSize.average, activeThreads.average, runningThreads.average, queuedTaskCount.average,
          poolSize.max, activeThreads.max, runningThreads.max, queuedTaskCount.max))
    }
  }

  def logThreadPoolExecutor(name: String, threadPoolMetrics: EntitySnapshot): Unit = {
    for {
      corePoolSize ← threadPoolMetrics.gauge("core-pool-size")
      maxPoolSize ← threadPoolMetrics.gauge("max-pool-size")
      poolSize ← threadPoolMetrics.gauge("pool-size")
      activeThreads ← threadPoolMetrics.gauge("active-threads")
      processedTasks ← threadPoolMetrics.gauge("processed-tasks")
    } {

      log.info(
        """
          |+--------------------------------------------------------------------------------------------------+
          ||  Thread-Pool-Executor                                                                            |
          ||                                                                                                  |
          ||  Dispatcher: %-83s |
          ||                                                                                                  |
          ||  Core Pool Size: %-4s                                                                            |
          ||  Max  Pool Size: %-4s                                                                            |
          ||                                                                                                  |
          ||                                                                                                  |
          ||                         Pool Size        Active Threads          Processed Task                  |
          ||           Min              %-4s                %-4s                   %-4s                       |
          ||           Avg              %-4s                %-4s                   %-4s                       |
          ||           Max              %-4s                %-4s                   %-4s                       |
          ||                                                                                                  |
          |+--------------------------------------------------------------------------------------------------+"""
          .stripMargin.format(name,
          corePoolSize.max,
          maxPoolSize.max,
          poolSize.min, activeThreads.min, processedTasks.min,
          poolSize.average, activeThreads.average, processedTasks.average,
          poolSize.max, activeThreads.max, processedTasks.max))
    }

  }

  def logSystemMetrics(metric: String, snapshot: EntitySnapshot): Option[Statistic] = metric match {
    case "cpu"              ⇒ logCpuMetrics(snapshot)
    case "network"          ⇒ logNetworkMetrics(snapshot)
    case "process-cpu"      ⇒ logProcessCpuMetrics(snapshot)
    case "context-switches" ⇒ logContextSwitchesMetrics(snapshot)
    case ignoreOthers       ⇒
      log.info("others command: {}", ignoreOthers)
      None
  }

//  def logCpuMetrics(cpuMetrics: EntitySnapshot): Unit = {
//    for {
//      user ← cpuMetrics.histogram("cpu-user")
//      system ← cpuMetrics.histogram("cpu-system")
//      cpuWait ← cpuMetrics.histogram("cpu-wait")
//      idle ← cpuMetrics.histogram("cpu-idle")
//    } {
//
//      log.info(
//        """
//          |+--------------------------------------------------------------------------------------------------+
//          ||                                                                                                  |
//          ||    CPU (ALL)                                                                                     |
//          ||                                                                                                  |
//          ||    User (percentage)       System (percentage)    Wait (percentage)   Idle (percentage)          |
//          ||       Min: %-3s                   Min: %-3s               Min: %-3s           Min: %-3s              |
//          ||       Avg: %-3s                   Avg: %-3s               Avg: %-3s           Avg: %-3s              |
//          ||       Max: %-3s                   Max: %-3s               Max: %-3s           Max: %-3s              |
//          ||                                                                                                  |
//          ||                                                                                                  |
//          |+--------------------------------------------------------------------------------------------------+"""
//          .stripMargin.format(
//          user.min, system.min, cpuWait.min, idle.min,
//          user.average, system.average, cpuWait.average, idle.average,
//          user.max, system.max, cpuWait.max, idle.max))
//    }
//
//  }

  def logCpuMetrics(cpuMetrics: EntitySnapshot): Option[CPUStatistic] = {
    for {
      user ← cpuMetrics.histogram("cpu-user")
      system ← cpuMetrics.histogram("cpu-system")
      cpuWait ← cpuMetrics.histogram("cpu-wait")
      idle ← cpuMetrics.histogram("cpu-idle")
    } yield{

      val statistic : CPUStatistic = new CPUStatistic(new PacketData(user.min , user.average , user.max) ,
        new PacketData(system.min , system.average , system.max) ,
        new PacketData(cpuWait.min , cpuWait.average , cpuWait.max) ,
        new PacketData(idle.min , idle.average , idle.max))
      statistic
    }
  }


//  def logNetworkMetrics(networkMetrics: EntitySnapshot): Unit = {
//    for {
//      rxBytes ← networkMetrics.histogram("rx-bytes")
//      txBytes ← networkMetrics.histogram("tx-bytes")
//      rxErrors ← networkMetrics.histogram("rx-errors")
//      txErrors ← networkMetrics.histogram("tx-errors")
//    } {
//
//      log.info(
//        """
//          |+--------------------------------------------------------------------------------------------------+
//          ||                                                                                                  |
//          ||    Network (ALL)                                                                                 |
//          ||                                                                                                  |
//          ||     Rx-Bytes (KB)                Tx-Bytes (KB)              Rx-Errors            Tx-Errors       |
//          ||      Min: %-4s                  Min: %-4s                 Total: %-8s      Total: %-8s  |
//          ||      Avg: %-4s                Avg: %-4s                                                     |
//          ||      Max: %-4s                  Max: %-4s                                                       |
//          ||                                                                                                  |
//          |+--------------------------------------------------------------------------------------------------+"""
//          .stripMargin.
//          format(
//            rxBytes.min, txBytes.min, rxErrors.sum, txErrors.sum,
//            rxBytes.average, txBytes.average,
//            rxBytes.max, txBytes.max))
//    }
//  }

  def logNetworkMetrics(networkMetrics: EntitySnapshot): Option[NetStatistic] = {
    for {
      rxBytes ← networkMetrics.histogram("rx-bytes")
      txBytes ← networkMetrics.histogram("tx-bytes")
      rxErrors ← networkMetrics.histogram("rx-errors")
      txErrors ← networkMetrics.histogram("tx-errors")
    } yield{

      val statistic : NetStatistic = new NetStatistic(new PacketData(rxBytes.min , rxBytes.average , rxBytes.max) ,
        new PacketData(txBytes.min , txBytes.average , txBytes.max) , rxErrors.sum , txErrors.sum)
      statistic
    }
  }

//  def logProcessCpuMetrics(processCpuMetrics: EntitySnapshot): Unit = {
//    for {
//      user ← processCpuMetrics.histogram("process-user-cpu")
//      total ← processCpuMetrics.histogram("process-cpu")
//    } {
//
//      log.info(
//        """
//          |+--------------------------------------------------------------------------------------------------+
//          ||                                                                                                  |
//          ||    Process-CPU                                                                                   |
//          ||                                                                                                  |
//          ||             User-Percentage                           Total-Percentage                           |
//          ||                Min: %-12s                         Min: %-12s                       |
//          ||                Avg: %-12s                         Avg: %-12s                       |
//          ||                Max: %-12s                         Max: %-12s                       |
//          ||                                                                                                  |
//          |+--------------------------------------------------------------------------------------------------+"""
//          .stripMargin.format(
//          user.min, total.min,
//          user.average, total.average,
//          user.max, total.max))
//    }
//
//  }

  def logProcessCpuMetrics(processCpuMetrics: EntitySnapshot): Option[ProcessCPUStatistic] = {
    for {
      user ← processCpuMetrics.histogram("process-user-cpu")
      total ← processCpuMetrics.histogram("process-cpu")
    } yield{

      val statistic : ProcessCPUStatistic = new ProcessCPUStatistic(new PacketData(user.min , user.average , user.max) ,
        new PacketData(total.min , total.average , total.max))
      statistic
    }

  }

//  def logContextSwitchesMetrics(contextSwitchMetrics: EntitySnapshot): Unit = {
//    for {
//      perProcessVoluntary ← contextSwitchMetrics.histogram("context-switches-process-voluntary")
//      perProcessNonVoluntary ← contextSwitchMetrics.histogram("context-switches-process-non-voluntary")
//      global ← contextSwitchMetrics.histogram("context-switches-global")
//    } {
//
//      log.info(
//        """
//          |+--------------------------------------------------------------------------------------------------+
//          ||                                                                                                  |
//          ||    Context-Switches                                                                              |
//          ||                                                                                                  |
//          ||        Global                Per-Process-Non-Voluntary            Per-Process-Voluntary          |
//          ||    Min: %-12s                   Min: %-12s                  Min: %-12s      |
//          ||    Avg: %-12s                   Avg: %-12s                  Avg: %-12s      |
//          ||    Max: %-12s                   Max: %-12s                  Max: %-12s      |
//          ||                                                                                                  |
//          |+--------------------------------------------------------------------------------------------------+"""
//          .stripMargin.
//          format(
//            global.min, perProcessNonVoluntary.min, perProcessVoluntary.min,
//            global.average, perProcessNonVoluntary.average, perProcessVoluntary.average,
//            global.max, perProcessNonVoluntary.max, perProcessVoluntary.max))
//    }
//
//  }

  def logContextSwitchesMetrics(contextSwitchMetrics: EntitySnapshot): Option[SwitchStatistic] = {
    for {
      perProcessVoluntary ← contextSwitchMetrics.histogram("context-switches-process-voluntary")
      perProcessNonVoluntary ← contextSwitchMetrics.histogram("context-switches-process-non-voluntary")
      global ← contextSwitchMetrics.histogram("context-switches-global")
    } yield{

      val statistic : SwitchStatistic = new SwitchStatistic(new PacketData(global.min , global.average , global.max) ,
        new PacketData(perProcessNonVoluntary.min , perProcessNonVoluntary.average , perProcessNonVoluntary.max) ,
        new PacketData(perProcessVoluntary.min , perProcessVoluntary.average , perProcessVoluntary.max))
      statistic
    }

  }

  def logTraceMetrics(name: String, traceSnapshot: EntitySnapshot): Unit = {
    val traceMetricsData = StringBuilder.newBuilder

    for {
      elapsedTime ← traceSnapshot.histogram("elapsed-time")
    } {

      traceMetricsData.append(
        """
          |+--------------------------------------------------------------------------------------------------+
          ||                                                                                                  |
          ||    Trace: %-83s    |
          ||    Count: %-8s                                                                               |
          ||                                                                                                  |
          ||  Elapsed Time (nanoseconds):                                                                     |
          |"""
          .stripMargin.format(
          name, elapsedTime.numberOfMeasurements))

      traceMetricsData.append(compactHistogramView(elapsedTime))
      traceMetricsData.append(
        """
          ||                                                                                                  |
          |+--------------------------------------------------------------------------------------------------+"""
          .
            stripMargin)

      log.info(traceMetricsData.toString())
    }
  }

  def logMetrics(histograms: Map[String, Option[Histogram.Snapshot]],
                 counters: Map[String, Option[Counter.Snapshot]], minMaxCounters: Map[String, Option[Histogram.Snapshot]],
                 gauges: Map[String, Option[Histogram.Snapshot]]): Unit = {

    if (histograms.isEmpty && counters.isEmpty && minMaxCounters.isEmpty && gauges.isEmpty) {
      log.info("No metrics reported")
      return
    }

    val metricsData = StringBuilder.newBuilder

    metricsData.append(
      """
        |+--------------------------------------------------------------------------------------------------+
        ||                                                                                                  |
        ||                                         Counters                                                 |
        ||                                       -------------                                              |
        |""".stripMargin)

    counters.foreach { case (name, snapshot) ⇒ metricsData.append(userCounterString(name, snapshot.get)) }

    metricsData.append(
      """||                                                                                                  |
        ||                                                                                                  |
        ||                                        Histograms                                                |
        ||                                      --------------                                              |
        |""".stripMargin)

    histograms.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(compactHistogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        ||                                      MinMaxCounters                                              |
        ||                                    -----------------                                             |
        |""".stripMargin)

    minMaxCounters.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(histogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        ||                                          Gauges                                                  |
        ||                                        ----------                                                |
        |"""
        .stripMargin)

    gauges.foreach {
      case (name, snapshot) ⇒
        metricsData.append("|  %-40s                                                        |\n".format(name))
        metricsData.append(histogramView(snapshot.get))
        metricsData.append("\n|                                                                                                  |\n")
    }

    metricsData.append(
      """||                                                                                                  |
        |+--------------------------------------------------------------------------------------------------+"""
        .stripMargin)

    log.info(metricsData.toString())
  }

  def userCounterString(counterName: String, snapshot: Counter.Snapshot): String = {
    "|             %30s  =>  %-12s                                     |\n"
      .format(counterName, snapshot.count)
  }

  def compactHistogramView(histogram: Histogram.Snapshot): String = {
    val sb = StringBuilder.newBuilder

    sb.append("|    Min: %-11s  50th Perc: %-12s   90th Perc: %-12s   95th Perc: %-12s |\n".format(
      histogram.min, histogram.percentile(50.0D), histogram.percentile(90.0D), histogram.percentile(95.0D)))
    sb.append("|                      99th Perc: %-12s 99.9th Perc: %-12s         Max: %-12s |".format(
      histogram.percentile(99.0D), histogram.percentile(99.9D), histogram.max))

    sb.toString()
  }

  def histogramView(histogram: Histogram.Snapshot): String =
    "|          Min: %-12s           Average: %-12s                Max: %-12s      |"
      .format(histogram.min, histogram.average, histogram.max)
}

object PerfReporterSubscriber {

  implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D
      else histogram.sum / histogram.numberOfMeasurements
    }
  }
}