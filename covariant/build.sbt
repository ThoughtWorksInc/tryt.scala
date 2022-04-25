enablePlugins(Example)

import scala.meta._
exampleSuperTypes += init"_root_.org.scalatest.Inside"

libraryDependencies += "org.scalaz" %%% "scalaz-core" % "7.4.0-M11"

libraryDependencies += "org.scalaz" %%% "scalaz-effect" % "7.4.0-M11"

libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.12" % Test

libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _))  => Nil
    case Some((2, 13)) => Seq(compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full))
  }
}

ThisBuild / scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _))                  => Seq("-Ykind-projector:underscores")
    case Some((2, 13)) | Some((2, 12)) => Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
  }
}

sourceGenerators in Test := {
  (sourceGenerators in Test).value.filterNot { sourceGenerator =>
    import Ordering.Implicits._
    VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L) &&
    sourceGenerator.info
      .get(taskDefinitionKey)
      .exists { scopedKey: ScopedKey[_] =>
        scopedKey.key == generateExample.key
      }
  }
}
