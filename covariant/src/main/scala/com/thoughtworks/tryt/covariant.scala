package com.thoughtworks.tryt

import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scalaz.Tags.Parallel
import scalaz.effect.{IO, LiftIO, MonadCatchIO, MonadIO}
import scalaz.{\/, _}

/** The namespace that contains the covariant [[TryT]]. */
object covariant {

  private[tryt] trait OpacityTypes {
    type TryT[F[+ _], +A]

    def apply[F[+ _], A](run: F[Try[A]]): TryT[F, A]
    def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]]

  }

  @inline
  private[tryt] val opacityTypes: OpacityTypes = new Serializable with OpacityTypes {

    type TryT[F[+ _], +A] = F[Try[A]]

    override final def apply[F[+ _], A](tryT: F[Try[A]]): TryT[F, A] = tryT

    override final def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]] = tryT

  }

  object TryT extends TryTInstances0 {
    private[thoughtworks] def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]] = opacityTypes.unwrap(tryT)

    /** @group Converters */
    def unapply[F[+ _], A](tryT: TryT[F, A]): Some[F[Try[A]]] = Some(unwrap(tryT))

    /** @group Converters */
    def apply[F[+ _], A](tryT: F[Try[A]]): TryT[F, A] = opacityTypes.apply(tryT)

    /** @group Type classes */
    implicit final def tryTMonadCatchIORec[F[+ _]](
        implicit F0: MonadIO[F],
        B0: BindRec[F]): MonadCatchIO[TryT[F, ?]] with BindRec[TryT[F, ?]] = {
      new Serializable with MonadCatchIO[TryT[F, ?]] with TryTBindRec[F] with TryTLiftIO[F] with TryTMonadError[F]  {
        override implicit def B: BindRec[F] = B0
        override implicit def F: MonadIO[F] = F0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances3 { this: TryT.type =>

    /** @group Type classes */
    implicit final def tryTMonadCatchIO[F[+ _]](implicit F0: MonadIO[F]): MonadCatchIO[TryT[F, ?]] = {
      new Serializable with MonadCatchIO[TryT[F, ?]] with TryTLiftIO[F] with TryTMonadError[F] {
        override implicit def F: MonadIO[F] = F0
      }
    }

    /** @group Type classes */
    implicit final def tryTParallelApplicative[F[+ _]](
        implicit F0: Applicative[Lambda[A => F[A] @@ Parallel]],
        S0: Semigroup[Throwable]): Applicative[Lambda[A => TryT[F, A] @@ Parallel]] = {
      new Serializable with TryTParallelApplicative[F]  {
        override implicit def F: Applicative[Lambda[A => F[A] @@ Parallel]] = F0
        override implicit def S: Semigroup[Throwable] = S0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances2 extends TryTInstances3 { this: TryT.type =>

    /** @group Type classes */
    implicit final def tryTBindRec[F[+ _]](
        implicit F0: Monad[F],
        B0: BindRec[F]): BindRec[TryT[F, ?]] with MonadError[TryT[F, ?], Throwable] = {
      new Serializable with TryTBindRec[F] with TryTMonadError[F]  {
        override implicit def B: BindRec[F] = B0
        override implicit def F: Monad[F] = F0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances1 extends TryTInstances2 { this: TryT.type =>

    /** @group Type classes */
    implicit final def tryTMonadError[F[+ _]](implicit F0: Monad[F]): MonadError[TryT[F, ?], Throwable] = {
      new Serializable with TryTMonadError[F]  {
        implicit override def F: Monad[F] = F0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances0 extends TryTInstances1 { this: TryT.type =>

    /** @group Type classes */
    implicit final def tryTLiftIO[F[+ _]](implicit F0: LiftIO[F]): LiftIO[TryT[F, ?]] =
      new Serializable with TryTLiftIO[F]  {
        implicit override def F: LiftIO[F] = F0
      }

    /** @group Type classes */
    implicit final def tryTFunctor[F[+ _]](implicit F0: Functor[F]): Functor[TryT[F, ?]] =
      new Serializable with TryTFunctor[F] {
        implicit override def F: Functor[F] = F0
      }
  }

  import opacityTypes._

  private[tryt] trait TryTFunctor[F[+ _]] extends Functor[TryT[F, ?]] {
    implicit protected def F: Functor[F]

    override def map[A, B](fa: TryT[F, A])(f: A => B): TryT[F, B] = {
      opacityTypes.apply(F.map(unwrap(fa)) { tryA =>
        tryA.flatMap { a =>
          Try(f(a))
        }
      })
    }
  }

  private[tryt] trait TryTBind[F[+ _]] extends Bind[TryT[F, ?]] with TryTFunctor[F] {
    implicit protected override def F: Monad[F]

    override def bind[A, B](fa: TryT[F, A])(f: A => TryT[F, B]): TryT[F, B] = opacityTypes {
      F.bind[Try[A], Try[B]](unwrap(fa)) {
        case Failure(e) => F.point(Failure(e))
        case Success(value) =>
          unwrap(
            try {
              f(value)
            } catch {
              case NonFatal(e) =>
                opacityTypes.apply[F, B](F.point(Failure(e)))
            }
          )
      }
    }
  }

  private[tryt] trait TryTBindRec[F[+ _]] extends BindRec[TryT[F, ?]] with TryTBind[F] {
    implicit protected def F: Monad[F]
    implicit protected def B: BindRec[F]

    override def tailrecM[A, B](f: A => TryT[F, A \/ B])(a: A): TryT[F, B] = {

      val fTryB: F[Try[B]] = B.tailrecM[A, Try[B]](a =>
        Try(f(a)) match {
          case Success(tryT) =>
            F.map(opacityTypes.unwrap(tryT)) {
              case Failure(e) =>
                \/-(Failure(e))
              case Success(\/-(b)) =>
                \/-(Success(b))
              case Success(left @ -\/(_)) =>
                left
            }
          case Failure(e) =>
            F.point(\/-(Failure(e)))
      })(a)
      opacityTypes(fTryB)
    }
  }

  private[tryt] trait TryTLiftIO[F[+ _]] extends LiftIO[TryT[F, ?]] {
    implicit protected def F: LiftIO[F]

    override def liftIO[A](ioa: IO[A]): TryT[F, A] = {
      opacityTypes.apply(F.liftIO(ioa.map(Try(_))))
    }

  }

  private[tryt] trait TryTMonadError[F[+ _]] extends MonadError[TryT[F, ?], Throwable] with TryTBind[F] {
    implicit protected override def F: Monad[F]

    override def point[A](a: => A): TryT[F, A] = opacityTypes.apply(F.point(Try(a)))

    override def raiseError[A](e: Throwable): TryT[F, A] = opacityTypes.apply[F, A](F.point(Failure(e)))

    override def handleError[A](fa: TryT[F, A])(f: (Throwable) => TryT[F, A]): TryT[F, A] = opacityTypes {
      F.bind(unwrap(fa)) {
        case Failure(e) =>
          unwrap(
            try {
              f(e)
            } catch {
              case NonFatal(nonFatal) => opacityTypes[F, A](F.point(Failure(nonFatal)))
            }
          )
        case Success(value) => F.point(Success(value))
      }
    }

    def except[A](fa: TryT[F, A])(handler: Throwable => TryT[F, A]): TryT[F, A] = {
      handleError(fa)(handler)
    }
  }

  private[tryt] trait TryTParallelApplicative[F[+ _]] extends Applicative[Lambda[A => TryT[F, A] @@ Parallel]] {
    implicit protected def F: Applicative[Lambda[A => F[A] @@ Parallel]]
    implicit protected def S: Semigroup[Throwable]
    private type T[+A] = TryT[F, A]
    private type P[A] = T[A] @@ Parallel

    override def point[A](a: => A): P[A] =
      Parallel(opacityTypes[F, A] {
        Parallel.unwrap(F.point(Try(a)))
      })

    override def map[A, B](fa: P[A])(f: (A) => B): P[B] = {
      Parallel(opacityTypes.apply(Parallel.unwrap(F.map(Parallel[F[Try[A]]](unwrap(Parallel.unwrap(fa)))) { tryA =>
        tryA.flatMap { a =>
          Try(f(a))
        }
      })))
    }

    override def ap[A, B](fa: => P[A])(f: => P[(A) => B]): P[B] = {

      val fTryAP: F[Try[A]] @@ Parallel = try {
        Parallel(opacityTypes.unwrap(Parallel.unwrap(fa)))
      } catch {
        case NonFatal(e) =>
          F.point(Failure(e))
      }

      val fTryABP: F[Try[A => B]] @@ Parallel = try {
        Parallel(opacityTypes.unwrap(Parallel.unwrap(f)): F[Try[A => B]])
      } catch {
        case NonFatal(e) =>
          F.point(Failure(e))
      }

      import scalaz.syntax.all._

      val fTryBP: F[Try[B]] @@ Parallel =
        F.apply2(fTryAP, fTryABP) { (tryA: Try[A], tryAB: Try[A => B]) =>
          tryA match {
            case Success(a) =>
              tryAB match {
                case Success(ab) =>
                  try {
                    Success(ab(a))
                  } catch {
                    case NonFatal(nonfatal) => Failure(nonfatal)
                  }
                case Failure(failure) => Failure(failure)
              }
            case Failure(failure) =>
              tryAB match {
                case Success(_) => Failure(failure)
                case Failure(anotherFailure) =>
                  Failure(failure |+| anotherFailure)
              }
          }
        }
      Parallel(opacityTypes.apply(Parallel.unwrap(fTryBP)))
    }
  }

  /** A monad transformer for exception handling.
    *
    * @see This `TryT` transfomer is similar to [[scalaz.EitherT]],
    *      except `TryT` handles exceptions thrown in callback functions passed to
    *      [[scalaz.Monad.map map]], [[scalaz.Monad.bind bind]] or [[scalaz.Monad.point point]].
    *
    * @example As a monad transformer, `TryT` should be used with another monadic data type, like [[scalaz.Name]].
    *
    *          {{{
    *          import scalaz.Name
    *          import com.thoughtworks.tryt.covariant.TryT, TryT._
    *
    *          type TryName[+A] = TryT[Name, A]
    *          }}}
    *
    *          Given a `validate` function,
    *
    *          {{{
    *          def validate(s: String): Int = s.toInt
    *          }}}
    *
    *          when creating a `TryT`-transformed [[scalaz.Name]] from the `validate`,
    *
    *          {{{
    *          import scalaz.syntax.all._
    *          val invalidTry: TryName[Int] = validate("invalid input").point[TryName]
    *          }}}
    *
    *          then the exceptions thrown in `validate` call should be converted to a [[scala.util.Failure]];
    *
    *          {{{
    *          import com.thoughtworks.tryt.covariant.TryT._
    *
    *          val TryT(Name(failure)) = invalidTry
    *
    *          import scala.util._
    *          failure should be(an[Failure[_]])
    *          }}}
    *
    *          and when there is no exception thrown in `validate` call,
    *
    *          {{{
    *          val validTry: TryName[Int] = validate("42").point[TryName]
    *          }}}
    *
    *          then the result of `validate` call should be converted to a [[scala.util.Success]];
    *
    *          {{{
    *          val TryT(Name(success)) = validTry
    *          success should be(Success(42))
    *          }}}
    *
    *          and when the `TryT`-transformed [[scalaz.Name]] is built from a `for`-comprehension,
    *
    *          {{{
    *          val invalidForComprehension: TryName[Int] = for {
    *            i <- validate("42").point[TryName]
    *            j <- validate("invalid input").point[TryName]
    *          } yield i + j
    *          }}}
    *
    *          then the exceptions thrown in the `for`-comprehension should be converted to a [[scala.util.Failure]];
    *
    *          {{{
    *          val TryT(Name(failure2)) = invalidTry
    *          failure2 should be(an[Failure[_]])
    *          }}}
    *
    *
    * @note This `TryT` type is an opacity alias to `F[Try[A]]`.
    *       All type classes and helper functions for this `TryT` type are defined in the companion object [[TryT$ TryT]]
    * @template
    */
  type TryT[F[+ _], +A] = opacityTypes.TryT[F, A]

}
