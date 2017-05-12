import sbt.Keys.libraryDependencies
import sbt.addCompilerPlugin

import scala.util.matching.Regex.{Groups, Match}

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")

lazy val commonSetting = Seq(
  libraryDependencies += "org.scalaz" %%% "scalaz-core" % "7.2.12",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.2.12" % Test,
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

val CovariantRegex = """covariant|\+\s*([A_])\b""".r

lazy val invariant = crossProject
  .crossType(CrossType.Pure)
  .settings(
    commonSetting,
    for (configuration <- Seq(Compile, Test))
      yield {
        sourceGenerators in configuration += Def.task {
          for {
            covariantFile <- (unmanagedSources in configuration in covariantJVM).value
            covariantDirectory <- (unmanagedSourceDirectories in configuration in covariantJVM).value
            relativeFile <- covariantFile.relativeTo(covariantDirectory)
          } yield {
            val covariantSource = IO.read(covariantFile, scala.io.Codec.UTF8.charSet)

            val doubleSource = CovariantRegex.replaceAllIn(covariantSource, (_: Match) match {
              case Match("covariant") => "invariant"
              case Groups(name @ ("A" | "_")) => name
            })

            val outputFile = (sourceManaged in configuration).value / relativeFile.getPath
            IO.write(outputFile, doubleSource, scala.io.Codec.UTF8.charSet)
            outputFile
          }
        }.taskValue
      }
  )

lazy val covariant = crossProject
  .crossType(CrossType.Pure)
  .settings(commonSetting)

lazy val invariantJVM = invariant.jvm

lazy val invariantJS = invariant.js

lazy val covariantJVM = covariant.jvm

lazy val covariantJS = covariant.js

organization in ThisBuild := "com.thoughtworks.tryt"

publishArtifact := false

lazy val unidoc = project
  .enablePlugins(StandaloneUnidoc, TravisUnidocTitle)
  .settings(
    UnidocKeys.unidocProjectFilter in ScalaUnidoc in UnidocKeys.unidoc := inProjects(invariantJVM, covariantJVM),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalacOptions += "-Xexperimental"
  )
