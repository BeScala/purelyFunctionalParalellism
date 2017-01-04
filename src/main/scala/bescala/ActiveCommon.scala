package bescala

import Util.{Atomic, whenDefinedGet}

/**
  * Created by lucd on 1/4/17.
  */
trait ActiveCommon extends Common {

  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es => {
      def ma: M[A] = parA(es)
      toM(a2b(fromM(ma)))
    }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => {
      var optionalMa: Option[M[A]] = None
      var optionalMb: Option[M[B]] = None

      val atomicOptionalMc: Atomic[M[C]] = new Atomic[M[C]]

      val combinerActor = new Actor[Either[M[A], M[B]]](es)({
        case Left(ma) =>
          if (optionalMb.isDefined) atomicOptionalMc.setValue(toM(ab2c(fromM(ma), fromM(optionalMb.get))))
          else optionalMa = Some(ma)
        case Right(mb) =>
          if (optionalMa.isDefined) atomicOptionalMc.setValue(toM(ab2c(fromM(optionalMa.get), fromM(mb))))
          else optionalMb = Some(mb)
      })
      val ma: M[A] = parA(es)
      val mb: M[B] = parB(es)
      combinerActor ! Left(ma)
      combinerActor ! Right(mb)

      atomicOptionalMc.getValue

    }

  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => {
      val ma: M[A] = parA(es)
      (a2pb(fromM(ma)))(es)
    }

}

/*

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    val callbackA: A => Unit = a => atomic.setValue(a)
    ma(callbackA)
    atomic.getValue
  }

 */