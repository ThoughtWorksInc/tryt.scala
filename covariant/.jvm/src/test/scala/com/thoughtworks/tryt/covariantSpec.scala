package com.thoughtworks.tryt

import com.thoughtworks.tryt.covariant.TryT
import com.thoughtworks.tryt.covariant.TryT._
import org.scalatest.{Assertion, AsyncFreeSpec, Inside, Matchers}

import scala.concurrent.Promise
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}
import scalaz.Tags.Parallel
import scalaz.{-\/, @@, Applicative, BindRec, Functor, MonadError, Semigroup, \/, \/-}
object covariantSpec {
  type ContUnit[+A] = scalaz.Cont[Unit, A]
  final case class Boom() extends Throwable

  final case class AnotherBoom() extends Throwable

  /** An exception that contains multiple Throwables. */
  final case class MultipleException(throwableSet: Set[Throwable])
      extends Exception("Multiple exceptions found")
      with NoStackTrace {
    override def toString: String = throwableSet.toString()
  }

  implicit def throwableSemigroup = new Semigroup[Throwable] {
    override def append(f1: Throwable, f2: => Throwable): Throwable =
      f1 match {
        case MultipleException(exceptionSet1) =>
          f2 match {
            case MultipleException(exceptionSet2) => MultipleException(exceptionSet1 ++ exceptionSet2)
            case _: Throwable                     => MultipleException(exceptionSet1 + f2)
          }
        case _: Throwable =>
          f2 match {
            case MultipleException(exceptionSet2) => MultipleException(exceptionSet2 + f1)
            case _: Throwable                     => MultipleException(Set(f1, f2))
          }
      }
  }
}

/** @author
  *   杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class covariantSpec extends AsyncFreeSpec with Matchers with Inside {

  import covariantSpec._

  "Given a TryT transformed ContUnit of Int" - {
    val futureTryInt: ContUnit[Try[Int]] = ContUnit.now(Try(3))
    val parallelFutureInt: TryT[ContUnit, Int] = TryT(futureTryInt)
    "When map it to another Int" - {

      "And the mapping function works fine" - {
        val result: TryT[ContUnit, Int] = Functor[TryT[ContUnit, ?]].map[Int, Int](parallelFutureInt) { int =>
          int * int
        }
        val TryT(unwrap) = result
        "Then the result should be as expected" in {
          val p = Promise[Assertion]

          unwrap.unsafePerformAsync { tryInt =>
            inside(tryInt) { case Success(value) =>
              p.success {
                value should be(9)
              }
            }
          }
          p.future
        }
      }

      "And the mapping function throws an exception" - {
        val result: TryT[ContUnit, Int] = Functor[TryT[ContUnit, ?]].map[Int, Int](parallelFutureInt) { int =>
          throw Boom()
          int * int
        }

        val unwrap: ContUnit[Try[Int]] = TryT.unwrap(result)
        "Then the exception should be found in a Failure in the result ContUnit" in {

          val p = Promise[Assertion]

          unwrap.unsafePerformAsync { tryInt =>
            inside(tryInt) { case Failure(e) =>
              p.success {
                e should be(a[Boom])
              }
            }
          }
          p.future
        }
      }
    }
  }

  "TryTMonadError point without exception" in {
    val tryTFutureInt: TryT[ContUnit, Int] =
      MonadError[TryT[ContUnit, ?], Throwable].point(1)

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Success(value) =>
        p.success {
          value should be(1)
        }
      }
    }
    p.future
  }

  "TryTMonadError point with exception" in {
    val tryTFutureInt: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].point {
      throw Boom()
      1
    }

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

  "TryTMonadError raiseError" in {
    val tryTFutureInt: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].raiseError(Boom())

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

  "TryTMonadError handleError" in {

    val error: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].raiseError(Boom())

    val tryTFutureInt: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].handleError(error) { throwable =>
      MonadError[TryT[ContUnit, ?], Throwable].point(1)
    }

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Success(value) =>
        p.success {
          value should be(1)
        }
      }
    }
    p.future
  }

  "TryTMonadError handleError -- when handler throw exception" in {

    val error: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].raiseError(Boom())

    val tryTFutureInt: TryT[ContUnit, Int] = MonadError[TryT[ContUnit, ?], Throwable].handleError(error) { throwable =>
      throw throwable
      MonadError[TryT[ContUnit, ?], Throwable].point(1)
    }

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

  implicit object FutureBindRec extends BindRec[ContUnit] {
    override def tailrecM[A, B](f: (A) => ContUnit[\/[A, B]])(a: A): ContUnit[B] = {
      f(a).flatMap {
        case \/-(b) => ContUnit.futureInstance.point(b)
        case -\/(a) => tailrecM(f)(a)
      }
    }

    override def bind[A, B](fa: ContUnit[A])(f: (A) => ContUnit[B]): ContUnit[B] = ContUnit.futureInstance.bind(fa)(f)

    override def map[A, B](fa: ContUnit[A])(f: (A) => B): ContUnit[B] = ContUnit.futureInstance.map(fa)(f)
  }

  "TryTBindRec with failure" in {

    val tryTFutureInt: TryT[ContUnit, Int] = BindRec[TryT[ContUnit, ?]].tailrecM { a: Int =>
      TryT[ContUnit, Int \/ Int](
        ContUnit.now(
          Failure[Int \/ Int](
            Boom()
          )
        )
      )
    }(0)

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

  "TryTBindRec f throw exception" in {

    val tryTFutureInt: TryT[ContUnit, Int] = BindRec[TryT[ContUnit, ?]].tailrecM { a: Int =>
      throw AnotherBoom()
      TryT(
        ContUnit.now(
          Try[Int \/ Int](
            try {
              \/-(a)
            } catch {
              case NonFatal(_) => -\/(1)
            }
          )
        )
      )
    }(0)

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[AnotherBoom])
        }
      }
    }
    p.future
  }

  "TryTBindRec should not stackOverFlow" in {

    var flag: Int = 0

    val tryTFutureInt: TryT[ContUnit, Int] = BindRec[TryT[ContUnit, ?]].tailrecM { a: Int =>
      TryT(
        ContUnit.now(
          Try[Int \/ Int](
            try {
              if (flag < 10000) {
                flag += 1
                -\/(a)
              } else {
                \/-(flag)
              }
            } catch {
              case NonFatal(_) => -\/(1)
            }
          )
        )
      )
    }(0)

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(tryTFutureInt)

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Success(value) =>
        p.success {
          value should be(10000)
        }
      }
    }
    p.future
  }

  "TryTParallelApplicative point without exception" in {

    val tryTFutureInt: TryT[ContUnit, Int] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point(1)

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(Parallel.unwrap(tryTFutureInt))

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Success(value) =>
        p.success {
          value should be(1)
        }
      }
    }
    p.future
  }

  "TryTParallelApplicative point with exception" in {

    val tryTFutureInt: TryT[ContUnit, Int] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point {
        throw Boom()
        1
      }

    val futureTryInt: ContUnit[Try[Int]] = TryT.unwrap(Parallel.unwrap(tryTFutureInt))

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

  "TryTParallelApplicative ap without exception" in {

    def fa: TryT[ContUnit, Int] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point(1)

    def f: TryT[ContUnit, Int => String] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point { int =>
        "String"
      }

    val tryTFutureInt: TryT[ContUnit, String] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].ap(fa)(f)

    val futureTryInt: ContUnit[Try[String]] = TryT.unwrap(Parallel.unwrap(tryTFutureInt))

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Success(value) =>
        p.success {
          value should be("String")
        }
      }
    }
    p.future
  }

  "TryTParallelApplicative ap with exception" in {

    def fa: TryT[ContUnit, Int] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point(1)

    def f: TryT[ContUnit, Int => String] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].point { int =>
        throw Boom()
        "String"
      }

    val tryTFutureInt: TryT[ContUnit, String] @@ Parallel =
      Applicative[λ[A => TryT[ContUnit, A] @@ Parallel]].ap(fa)(f)

    val futureTryInt: ContUnit[Try[String]] = TryT.unwrap(Parallel.unwrap(tryTFutureInt))

    val p = Promise[Assertion]

    futureTryInt.unsafePerformAsync { tryInt =>
      inside(tryInt) { case Failure(e) =>
        p.success {
          e should be(a[Boom])
        }
      }
    }
    p.future
  }

}
