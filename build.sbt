// Name of the project
name := "Printrbot G2 console"

// Project version
version := "0.0.1"

// Version of Scala used by the project
scalaVersion := "2.11.8"

// Add dependency on ScalaFX library
libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.102-R11"
// https://mvnrepository.com/artifact/com.neuronrobotics/nrjavaserial
libraryDependencies += "com.neuronrobotics" % "nrjavaserial" % "3.12.1"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.0"
// https://mvnrepository.com/artifact/org.reactivestreams/reactive-streams
libraryDependencies += "org.reactivestreams" % "reactive-streams" % "1.0.0.final"
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "3.0.1" % "test"
libraryDependencies += "org.mockito" % "mockito-core" % "2.7.1"


scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-encoding", "utf8", "-feature")

// Fork a new JVM for 'run' and 'test:run', to avoid JavaFX double initialization problems
fork := true
