package com.thoughtworks.tryt

import scala.language.higherKinds
import scala.util._
import scalaz._
import com.thoughtworks.tryt.invariant.TryT

private[tryt] trait InvariantInstances { this: TryT.type =>

  /** @group Type classes */
  implicit final def tryTMonadTrans: MonadTrans[TryT] = new MonadTrans[TryT] {
    override def liftM[G[_], A](a: G[A])(implicit monad: Monad[G]): TryT[G, A] = {
      TryT(monad.map(a)(Success(_)))
    }

    override implicit def apply[G[_]: Monad]: Monad[TryT[G, ?]] = tryTMonadError[G]
  }

}
