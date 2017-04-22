import sbt.Keys.libraryDependencies
import sbt.addCompilerPlugin

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")

lazy val commonSetting = Seq(
  libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.11",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.2.11" % Test,
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

commonSetting

lazy val tryt = (crossProject.crossType(CrossType.Pure) in file("."))
  .settings(
    commonSetting
  )

lazy val trytJVM = tryt.jvm

lazy val trytJS = tryt.js.settings(
  commonSetting,
  libraryDependencies += "org.scalaz" %%% "scalaz-core" % "7.2.11"
)

organization in ThisBuild := "com.thoughtworks.tryt"

publishArtifact := false
