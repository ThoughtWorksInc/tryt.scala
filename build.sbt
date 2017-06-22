import sbt.Keys.libraryDependencies
import sbt.addCompilerPlugin

import scala.util.matching.Regex.{Groups, Match}

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")

lazy val tryt = crossProject.crossType(CrossType.Pure)

lazy val trytJS = tryt.js.addSbtFiles(file("../build.sbt.shared"))

lazy val trytJVM = tryt.jvm.addSbtFiles(file("../build.sbt.shared"))

organization in ThisBuild := "com.thoughtworks.tryt"

publishArtifact := false

lazy val unidoc = project
  .enablePlugins(StandaloneUnidoc, TravisUnidocTitle)
  .settings(
    UnidocKeys.unidocProjectFilter in ScalaUnidoc in UnidocKeys.unidoc := inProjects(trytJVM),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalacOptions += "-Xexperimental"
  )
