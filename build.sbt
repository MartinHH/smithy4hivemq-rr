ThisBuild / scalaVersion := "3.3.1"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

// contains smithy-files and generated code
lazy val generated = (project in file("generated"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "generated",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// contains implementations of the generated service apis (i.e. the business logic),
// still independent of any runtime or interpreter
lazy val services = (project in file("services"))
  .dependsOn(generated)
  .settings(
    name := "services",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// the http(4s) server
lazy val http4s = (project in file("http4s"))
  .dependsOn(services)
  .settings(
    name := "http4s",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s" %% "http4s-ember-server" % "0.23.16"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )
