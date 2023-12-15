ThisBuild / scalaVersion := "3.3.1"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val log4catsVersion = "2.6.0"
lazy val logbackVersion = "1.4.7"

// mqtt-related smithy traits (and their generated scala representations)
lazy val smithyMqtt = (project in file("smithy-mqtt"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "smithy-mqtt",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// code for converting smithy4s-generated services (and their implementations) to
// request-response-handlers that target mqtt (without depending on a specific
// mqtt library), interpreting the traits from smithyMqtt
lazy val smithy4mqtt = (project in file("smithy4mqtt"))
  .dependsOn(smithyMqtt)
  .settings(
    name := "smithy4mqtt",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-json" % smithy4sVersion.value
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// code for installing the output from smithy4mqtt onto a hivemq-mqtt5-client
lazy val smithy4hivemq = (project in file("smithy4hivemq"))
  .dependsOn(smithy4mqtt)
  .settings(
    name := "smithy4hivemq",
    libraryDependencies ++= Seq(
      "com.hivemq" % "hivemq-mqtt-client" % "1.3.3"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// code for installing the output from smithy4mqtt onto a hivemq-mqtt5-client using cats-effect
lazy val smithy4hivemqCats = (project in file("smithy4hivemq-cats"))
  .dependsOn(smithy4hivemq)
  .settings(
    name := "smithy4hivemq-cats",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-cats" % smithy4sVersion.value,
      "co.fs2" %% "fs2-reactive-streams" % "3.9.2",
      "org.typelevel" %% "log4cats-core" % log4catsVersion
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// smithy-files and generated code for some example services
// (annotated for both http and mqtt)
lazy val exampleGeneratedServices = (project in file("example-generated-services"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(smithyMqtt)
  .settings(
    name := "example-generated-services",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// contains implementations of the generated service apis (i.e. the business logic)
lazy val exampleServices = (project in file("example-services"))
  .dependsOn(exampleGeneratedServices)
  .settings(
    name := "example-services",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.5.1"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// an mqtt client hosting the generated services
lazy val exampleHiveMqServer = (project in file("example-hivemq-server"))
  .dependsOn(exampleServices, smithy4hivemqCats)
  .settings(
    name := "example-hivemq-server",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// an http(4s) server hosting the generated services
lazy val exampleHttp4sServer = (project in file("example-http4s-server"))
  .dependsOn(exampleServices)
  .settings(
    name := "example-http4s-server",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s" %% "http4s-ember-server" % "0.23.18",
      "ch.qos.logback" % "logback-classic" % logbackVersion
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// combined http(4s) server and mqtt client hosting the generated services
lazy val exampleCombinedServer = (project in file("example-combined-server"))
  .dependsOn(exampleHiveMqServer, exampleHttp4sServer)
  .settings(
    name := "example-combined-server",
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )
