ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.4"

lazy val root = (project in file("."))
  .settings(
    name := "cap",
    libraryDependencies += "dev.zio" %% "zio" % "2.1.16"
  )
