# tryt.scala <a href="http://thoughtworks.com/"><img align="right" src="https://www.thoughtworks.com/imgs/tw-logo.png" title="ThoughtWorks" height="15"/></a>

[![Build Status](https://travis-ci.org/ThoughtWorksInc/tryt.scala.svg?branch=master)](https://travis-ci.org/ThoughtWorksInc/tryt.scala)
[![Latest version](https://index.scala-lang.org/thoughtworksinc/tryt.scala/covariant/latest.svg)](https://index.scala-lang.org/thoughtworksinc/tryt.scala/covariant)
[![Latest version](https://index.scala-lang.org/thoughtworksinc/tryt.scala/invariant/latest.svg)](https://index.scala-lang.org/thoughtworksinc/tryt.scala/invariant)
[![Scaladoc](https://javadoc.io/badge/com.thoughtworks.tryt/covariant_2.11.svg?label=scaladoc)](https://javadoc.io/page/com.thoughtworks.tryt/covariant_2.11/latest/com/thoughtworks/tryt/package.html)

**tryt.scala** contains [Scalaz](http://scalaz.org/) monad transformers for exception handling.

There are two monad transformers: the invariant `TryT` and covariant `TryT`. 
Unlike `scala.EitherT`, `TryT` handles native exceptions thrown by native Java or Scala methods.

### Covariant `TryT`
Covariant `TryT` works with monadic data types whose kind is `F[+A]`,
like `scalaz.concurrent.Future` or `scalaz.Name`.

To use covariant `TryT`, add the following setting to your `build.sbt`,

``` scala
libraryDependencies += "com.thoughtworks.tryt" %% "invariant" % "latest.release"
```

and check the [Scaladoc](https://javadoc.io/page/com.thoughtworks.tryt/covariant_2.11/latest/com/thoughtworks/tryt/covariant$$TryT.html) for usage.

### invariant `TryT`
Invariant `TryT` works with all monadic data types

To use invariant `TryT`, add the following setting to your `build.sbt`,
``` scala
libraryDependencies += "com.thoughtworks.tryt" %% "invariant" % "latest.release"
```

and check the [Scaladoc](https://javadoc.io/page/com.thoughtworks.tryt/invariant_2.11/latest/com/thoughtworks/tryt/invariant$$TryT.html) for usage.
