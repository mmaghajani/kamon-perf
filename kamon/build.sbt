name := "kamon"

version := "1.0"

scalaVersion := "2.11.8"

val kamonVersion = "0.5.1"


libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.1"
libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.4.1"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.1"
libraryDependencies += "io.kamon" %% "kamon-core" % kamonVersion
libraryDependencies += "io.kamon" %% "kamon-akka" % kamonVersion
//libraryDependencies += "io.kamon" %% "kamon-statsd" % kamonVersion
libraryDependencies += "io.kamon" %% "kamon-log-reporter" % kamonVersion
libraryDependencies += "io.kamon" %% "kamon-system-metrics" % kamonVersion
libraryDependencies += "org.aspectj" % "aspectjweaver" % "1.8.5"
//libraryDependencies += "io.kamon" %% "sigar-loader" % "1.6.5-rev001"

aspectjSettings

javaOptions <++= AspectjKeys.weaverOptions in Aspectj

fork in run := true
