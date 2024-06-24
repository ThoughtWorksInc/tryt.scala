import scala.util.matching.Regex.{Groups, Match}

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
            case Match("covariant")              => "invariant"
            case Groups(name @ ("A" | "_"))      => name
          }
        )

        val outputFile = (sourceManaged in configuration).value / relativeFile.getPath
        IO.write(outputFile, doubleSource, scala.io.Codec.UTF8.charSet)
        outputFile
      }
    }.taskValue
  }
}

lazy val covariant = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).build()

lazy val invariant = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .jvmSettings(copySource(covariant.jvm))
  .jsSettings(copySource(covariant.js))

organization in ThisBuild := "com.thoughtworks.tryt"

publish / skip := true

enablePlugins(ScalaUnidocPlugin)

unidocProjectFilter in ScalaUnidoc in unidoc := inProjects(invariant.jvm, covariant.jvm)

libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _))  => Nil
    case Some((2, 13)) => Seq(compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full))
  }
}

ThisBuild / scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _))                  => Seq("-Ykind-projector:underscores")
    case Some((2, 13)) | Some((2, 12)) => Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
  }
}

scalacOptions += "-Xexperimental"
