package bescala

import java.util.concurrent._

import language.{higherKinds, implicitConversions}
import Util.{async}

object FutureActive extends ActiveCommon {

  override type M[A] = Future[A]

  override def fromM[A](ma: M[A]): A = ma.get

  override def toM[A](a: => A): M[A] =
    new M[A] {
      def get = a
      def get(timeout: Long, units: TimeUnit) = get
      def isDone = true
      def isCancelled = false
      def cancel(evenIfRunning: Boolean): Boolean = false
    }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => {
      val ma = parA(es)
      val mb = parB(es)
      toM(ab2c(fromM(ma), fromM(mb)))
    }


  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      val ma: M[A] = parA(es)
      async(es)(fromM(ma))
    }
}