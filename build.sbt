crossScalaVersions := Seq("2.11.11", "2.12.2")

lazy val unidoc = project
  .enablePlugins(StandaloneUnidoc, TravisUnidocTitle)
  .settings(
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
    scalacOptions += "-Xexperimental"
  )

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.10"

libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.2.10" % Test

libraryDependencies += "com.chrisneveu" %% "macrame" % "1.2.5"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % Test

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

organization in ThisBuild := "com.thoughtworks.tryt"

publishArtifact := false
