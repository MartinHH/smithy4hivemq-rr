ThisBuild / scalaVersion := "3.3.1"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

// contains smithy-files and generated code
lazy val generated = (project in file("generated"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "smithy4s-generated",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )

// the http(4s) server
lazy val http4s = (project in file("http4s"))
  .dependsOn(generated)
  .settings(
    name := "smithy4s-http4s",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s" %% "http4s-ember-server" % "0.23.16"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )
