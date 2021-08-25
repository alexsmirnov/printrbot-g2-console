// Name of the project
name := "Printrbot G2 console"
retrieveManaged := true
// Project version
version := "0.0.2"
val ScalatraVersion = "2.5.3"
// Version of Scala used by the project
scalaVersion := "2.12.13"
val javaFxVersion = "15.0.1"
// dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value
// Add dependency on ScalaFX library
//libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.144-R12"
libraryDependencies += "org.scalafx" %% "scalafx" % "15.0.1-R21"

// Determine OS version of JavaFX binaries
lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}

lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map( m =>
  "org.openjfx" % s"javafx-$m" % javaFxVersion classifier osName
)
// https://mvnrepository.com/artifact/com.neuronrobotics/nrjavaserial
libraryDependencies += "com.neuronrobotics" % "nrjavaserial" % "5.2.1"
// https://mvnrepository.com/artifact/org.reactivestreams/reactive-streams
libraryDependencies += "org.reactivestreams" % "reactive-streams" % "1.0.3"
scalacOptions += "-Ypartial-unification"
// libraryDependencies += "org.typelevel" %% "cats-core" % "2.3.0"
libraryDependencies += "io.monix" %% "monix" % "3.3.0"
libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.2.19.v20160908",
  "javax.servlet" % "javax.servlet-api" % "3.1.0"
)
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "org.mockito" % "mockito-core" % "2.7.1" % "test"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
libraryDependencies += "com.panayotis" % "appbundler" % "1.1.0" % "test"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-encoding", "utf8", "-feature")

// Fork a new JVM for 'run' and 'test:run', to avoid JavaFX double initialization problems
fork := true

assembly / assemblyJarName  := "pbconsole.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "ECLIPSEF.RSA", xs @ _*)         => MergeStrategy.discard
  case PathList("META-INF", "mailcap", xs @ _*)         => MergeStrategy.discard
  case PathList("org", "apache","commons", xs @ _*) => MergeStrategy.first
  case PathList("scala", "annotation", xs @ _*) => MergeStrategy.first
  case PathList("module-info.java") => MergeStrategy.discard
  case PathList("module-info.class") => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last == "Driver.properties" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last == "plugin.properties" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last == "log4j.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy ).value
    oldStrategy(x)
}
assembly / assemblyOption := (assembly / assemblyOption ).value.copy(includeScala = true, includeDependency = true)
assembly / assemblyExcludedJars := {
  val cp = (assembly / fullClasspath ).value
  cp filter {
      j => j.data.getName.startsWith("scalap-2") || 
      ( j.data.getName.startsWith("scala-") && 
        !j.data.getName.startsWith("scala-library") && 
        !j.data.getName.startsWith("scala-parser-combinators") && 
        !j.data.getName.startsWith("scala-reflect") )
      }
}
assembly / mainClass := Some("alexsmirnov.pbconsole.ConsoleApp")
test in assembly := {}
