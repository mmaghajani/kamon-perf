import sbt.Keys._

name := "kamon"

version := "1.0"

scalaVersion := "2.11.8"

val kamonVersion = "0.5.1"


libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.1"
libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.4.1"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.1"
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "2.4.7"
libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.4.7"
libraryDependencies += "io.kamon" %% "kamon-core" % kamonVersion
libraryDependencies += "io.kamon" %% "kamon-akka" % kamonVersion
//libraryDependencies += "io.kamon" %% "kamon-statsd" % kamonVersion
//libraryDependencies += "io.kamon" %% "kamon-log-reporter" % kamonVersion
libraryDependencies += "io.kamon" %% "kamon-system-metrics" % kamonVersion
libraryDependencies += "org.aspectj" % "aspectjweaver" % "1.8.5"
//libraryDependencies += "io.kamon" %% "sigar-loader" % "1.6.5-rev001"
libraryDependencies += "com.github.ben-manes.caffeine" % "caffeine" % "2.3.3"
libraryDependencies += "com.github.kxbmap" %% "configs" % "0.4.2"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.0"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.5"

aspectjSettings

javaOptions <++= AspectjKeys.weaverOptions in Aspectj

fork in run := true
