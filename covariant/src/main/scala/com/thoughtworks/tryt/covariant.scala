package com.thoughtworks.tryt

import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scalaz.Tags.Parallel
import scalaz.{\/, _}

object covariant {

  private[tryt] trait TryTExtractor {
    type TryT[F[+ _], +A]

    def apply[F[+ _], A](run: F[Try[A]]): TryT[F, A]
    def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]]

  }
  //TODO : @delegate
  @inline
  private[tryt] val TryTExtractor: TryTExtractor = new TryTExtractor {

    type TryT[F[+ _], +A] = F[Try[A]]

    override final def apply[F[+ _], A](tryT: F[Try[A]]): TryT[F, A] = tryT

    override final def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]] = tryT

  }

  object TryT extends TryTInstances0 {
    private[thoughtworks] def unwrap[F[+ _], A](tryT: TryT[F, A]): F[Try[A]] = TryTExtractor.unwrap(tryT)
    def unapply[F[+ _], A](tryT: TryT[F, A]): Some[F[Try[A]]] = Some(unwrap(tryT))
    def apply[F[+ _], A](tryT: F[Try[A]]): TryT[F, A] = TryTExtractor.apply(tryT)
  }

  private[tryt] sealed abstract class TryTInstances3 { this: TryT.type =>
    @inline
    implicit final def tryTParallelApplicative[F[+ _]](
        implicit F0: Applicative[Lambda[A => F[A] @@ Parallel]],
        S0: Semigroup[Throwable]): Applicative[Lambda[A => TryT[F, A] @@ Parallel]] = {
      new TryTParallelApplicative[F] {
        override implicit def F: Applicative[Lambda[A => F[A] @@ Parallel]] = F0
        override implicit def S: Semigroup[Throwable] = S0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances2 extends TryTInstances3 { this: TryT.type =>
    @inline
    implicit final def tryTBindRec[F[+ _]](
        implicit F0: Monad[F],
        B0: BindRec[F]): BindRec[TryT[F, ?]] with MonadError[TryT[F, ?], Throwable] = {
      new TryTBindRec[F] with TryTMonadError[F] {
        override implicit def B: BindRec[F] = B0
        override implicit def F: Monad[F] = F0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances1 extends TryTInstances2 { this: TryT.type =>
    @inline
    implicit final def tryTMonadError[F[+ _]](implicit F0: Monad[F]): MonadError[TryT[F, ?], Throwable] = {
      new TryTMonadError[F] {
        implicit override def F: Monad[F] = F0
      }
    }
  }

  private[tryt] sealed abstract class TryTInstances0 extends TryTInstances1 { this: TryT.type =>
    @inline
    implicit final def tryTFunctor[F[+ _]](implicit F0: Functor[F]): Functor[TryT[F, ?]] =
      new TryTFunctor[F] {
        implicit override def F: Functor[F] = F0
      }
  }

  import TryTExtractor._

  private[tryt] trait TryTFunctor[F[+ _]] extends Functor[TryT[F, ?]] {
    implicit protected def F: Functor[F]

    override def map[A, B](fa: TryT[F, A])(f: A => B): TryT[F, B] = {
      TryTExtractor.apply(F.map(unwrap(fa)) { tryA =>
        tryA.flatMap { a =>
          Try(f(a))
        }
      })
    }
  }

  private[tryt] trait TryTBind[F[+ _]] extends Bind[TryT[F, ?]] with TryTFunctor[F] {
    implicit protected override def F: Monad[F]

    override def bind[A, B](fa: TryT[F, A])(f: A => TryT[F, B]): TryT[F, B] = TryTExtractor {
      F.bind[Try[A], Try[B]](unwrap(fa)) {
        case Failure(e) => F.point(Failure(e))
        case Success(value) =>
          unwrap(
            try {
              f(value)
            } catch {
              case NonFatal(e) =>
                TryTExtractor.apply[F, B](F.point(Failure(e)))
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
            F.map(TryTExtractor.unwrap(tryT)) {
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
      TryTExtractor(fTryB)
    }
  }

  private[tryt] trait TryTMonadError[F[+ _]] extends MonadError[TryT[F, ?], Throwable] with TryTBind[F] {
    implicit protected override def F: Monad[F]

    override def point[A](a: => A): TryT[F, A] = TryTExtractor.apply(F.point(Try(a)))

    override def raiseError[A](e: Throwable): TryT[F, A] = TryTExtractor.apply[F, A](F.point(Failure(e)))

    override def handleError[A](fa: TryT[F, A])(f: (Throwable) => TryT[F, A]): TryT[F, A] = TryTExtractor {
      F.bind(unwrap(fa)) {
        case Failure(e) =>
          unwrap(
            try {
              f(e)
            } catch {
              case NonFatal(nonFatal) => TryTExtractor[F, A](F.point(Failure(nonFatal)))
            }
          )
        case Success(value) => F.point(Success(value))
      }
    }
  }

  private[tryt] trait TryTParallelApplicative[F[+ _]] extends Applicative[Lambda[A => TryT[F, A] @@ Parallel]] {
    implicit protected def F: Applicative[Lambda[A => F[A] @@ Parallel]]
    implicit protected def S: Semigroup[Throwable]
    private type T[+A] = TryT[F, A]
    private type P[A] = T[A] @@ Parallel

    override def point[A](a: => A): P[A] =
      Parallel(TryTExtractor[F, A] {
        Parallel.unwrap(F.point(Try(a)))
      })

    override def ap[A, B](fa: => P[A])(f: => P[(A) => B]): P[B] = {

      val fTryAP: F[Try[A]] @@ Parallel = try {
        Parallel(TryTExtractor.unwrap(Parallel.unwrap(fa)))
      } catch {
        case NonFatal(e) =>
          F.point(Failure(e))
      }

      val fTryABP: F[Try[A => B]] @@ Parallel = try {
        Parallel(TryTExtractor.unwrap(Parallel.unwrap(f)): F[Try[A => B]])
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
      Parallel(TryTExtractor.apply(Parallel.unwrap(fTryBP)))
    }
  }

  type TryT[F[+ _], +A] = TryTExtractor.TryT[F, A]

}
