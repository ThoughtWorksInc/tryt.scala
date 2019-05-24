libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.2.27" % Test

// Skip tests in Scala 2.13 for now due to https://github.com/scala/bug/issues/11068
unmanagedSourceDirectories in Test --= {
  import Ordering.Implicits._
  if (VersionNumber(scalaVersion.value).numbers >= Seq(2L, 13L)) {
    Seq((scalaSource in Test).value)
  } else {
    Nil
  }
}
