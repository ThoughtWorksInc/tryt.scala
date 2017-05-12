package com.thoughtworks.tryt

import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scalaz.Tags.Parallel
import scalaz.{\/, _}
import com.thoughtworks.tryt.invariant.TryT

private[tryt] trait InvariantInstances { this: TryT.type =>

  @inline
  implicit final def tryTMonadTrans: MonadTrans[TryT] = new MonadTrans[TryT] {
    override def liftM[G[_], A](a: G[A])(implicit monad: Monad[G]): TryT[G, A] = {
      TryT(monad.map(a)(Success(_)))
    }

    override implicit def apply[G[_]: Monad]: Monad[TryT[G, ?]] = tryTMonadError[G]
  }

}
