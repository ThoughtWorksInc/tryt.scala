import sbt.Keys.libraryDependencies
import sbt.addCompilerPlugin

import scala.util.matching.Regex.{Groups, Match}

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")

val CovariantRegex = """extends TryTInstances0|covariant|\+\s*([A_])\b""".r

def copySource(fromProject: Project) = {
  for (configuration <- Seq(Compile, Test)) yield {
    sourceGenerators in configuration += Def.task {
      for {
        covariantFile <- (unmanagedSources in configuration in fromProject).value
        covariantDirectory <- (unmanagedSourceDirectories in configuration in fromProject).value
        relativeFile <- covariantFile.relativeTo(covariantDirectory)
      } yield {
        val covariantSource = IO.read(covariantFile, scala.io.Codec.UTF8.charSet)

        val doubleSource = CovariantRegex.replaceAllIn(
          covariantSource,
          (_: Match) match {
            case Match("extends TryTInstances0") => "extends TryTInstances0 with InvariantInstances"
            case Match("covariant") => "invariant"
            case Groups(name @ ("A" | "_")) => name
          }
        )

        val outputFile = (sourceManaged in configuration).value / relativeFile.getPath
        IO.write(outputFile, doubleSource, scala.io.Codec.UTF8.charSet)
        outputFile
      }
    }.taskValue
  }
}

lazy val invariant = crossProject.crossType(CrossType.Pure)

lazy val covariant = crossProject.crossType(CrossType.Pure)

lazy val invariantJVM = invariant.jvm.addSbtFiles(file("../build.sbt.shared")).settings(copySource(covariantJVM))

lazy val invariantJS = invariant.js.addSbtFiles(file("../build.sbt.shared")).settings(copySource(covariantJS))

lazy val covariantJVM = covariant.jvm.addSbtFiles(file("../build.sbt.shared"))

lazy val covariantJS = covariant.js.addSbtFiles(file("../build.sbt.shared"))

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
