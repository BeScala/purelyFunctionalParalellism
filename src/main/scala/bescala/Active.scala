package bescala

import java.util.concurrent.TimeUnit

import bescala.Util.{Atomic, async}

import scala.language.{higherKinds, implicitConversions}

object Active extends Common {

  override type M[A] = java.util.concurrent.Future[A]

  // actively blocking get
  override def fromM[A](ma: M[A]): A = ma.get

  // we do not care to much about timeout
  override def toM[A](a: => A): M[A] = new M[A] {
    def get = a

    def get(timeout: Long, units: TimeUnit) = get

    def isDone = true

    def isCancelled = false

    def cancel(evenIfRunning: Boolean): Boolean = false
  }

  override def map[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    es => {
      val ma: M[A] = pa(es)
      toM(a2b(fromM(ma)))
    }

  // it is *very* important to define `ma` and `mb` outside of `toM(ab2c(fromM(ma), fromM(mb)))`
  //  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
  //  es => {
  //    val ma = pa(es)
  //    val mb = pb(es)
  //    toM(ab2c(fromM(ma), fromM(mb)))
  //  }

  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => {
      var optionalMa: Option[M[A]] = None
      var optionalMb: Option[M[B]] = None
      val atomicC: Atomic[M[C]] = new Atomic[M[C]]
      val combinerActor = new Actor[Either[M[A], M[B]]](es)({
        case Left(ma) =>
          if (optionalMb.isDefined) atomicC.setValue(toM(ab2c(ma.get, optionalMb.get.get)))
          else optionalMa = Some(ma)
        case Right(mb) =>
          if (optionalMa.isDefined) atomicC.setValue(toM(ab2c(optionalMa.get.get, mb.get)))
          else optionalMb = Some(mb)
      })
      combinerActor ! Left(pa(es))
      combinerActor ! Right(pb(es))

      atomicC.getValue
    }

  override def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => {
      val ma: M[A] = pa(es)
      a2pb(fromM(ma))(es)
    }

  override def fork[A](pa: => Par[A]): Par[A] =
    es => {
      val ma: M[A] = pa(es)
      async(es)(fromM(ma))
    }

}