// Name of the project
name := "Printrbot G2 console"

// Project version
version := "0.0.1"
val ScalatraVersion = "2.5.3"
// Version of Scala used by the project
scalaVersion := "2.11.11"
dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value
// Add dependency on ScalaFX library
libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.144-R12"
// https://mvnrepository.com/artifact/com.neuronrobotics/nrjavaserial
libraryDependencies += "com.neuronrobotics" % "nrjavaserial" % "3.12.1"
// https://mvnrepository.com/artifact/org.reactivestreams/reactive-streams
libraryDependencies += "org.reactivestreams" % "reactive-streams" % "1.0.0.final"
libraryDependencies += "org.typelevel" %% "cats-core" % "1.0.0-MF"
libraryDependencies ++= Seq(
  "io.monix" %% "monix" % "2.3.0",
  "io.monix" %% "monix-cats" % "2.3.0"
)
libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.2.19.v20160908",
  "javax.servlet" % "javax.servlet-api" % "3.1.0"
)
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "3.0.1" % "test"
libraryDependencies += "org.mockito" % "mockito-core" % "2.7.1"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-encoding", "utf8", "-feature")

// Fork a new JVM for 'run' and 'test:run', to avoid JavaFX double initialization problems
fork := true
