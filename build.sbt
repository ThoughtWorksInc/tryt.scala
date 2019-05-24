import scala.util.matching.Regex.{Groups, Match}

crossScalaVersions in ThisBuild := Seq("2.10.7", "2.11.12", "2.12.6", "2.13.0-RC2")

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

lazy val invariantJVM = invariant.jvm.settings(copySource(covariantJVM))

lazy val invariantJS = invariant.js.settings(copySource(covariantJS))

lazy val covariantJVM = covariant.jvm

lazy val covariantJS = covariant.js

organization in ThisBuild := "com.thoughtworks.tryt"

publishArtifact := false

lazy val unidoc = project
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.1"),
    scalacOptions += "-Xexperimental"
  )
