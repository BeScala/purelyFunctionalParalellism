package bescala

import bescala.Util._

import scala.language.{higherKinds, implicitConversions}

object Reactive extends Common {

  override type M[A] = (A => Unit) => Unit

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    ma(atomic.setValue(_))
    atomic.getValue
  }

  override def toM[A](a: => A): M[A] =
    callback =>
      callback(a)

  override def map[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    es => callbackB =>
      parA(es) { a => async(es)(callbackB(a2b(a))) }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => callbackC => {
      var optionalA: Option[A] = None
      var optionalB: Option[B] = None
      val combinerActor = new Actor[Either[A, B]](es)({
        case Left(a) =>
          if (optionalB.isDefined) async(es)(callbackC(ab2c(a, optionalB.get)))
          else optionalA = Some(a)
        case Right(b) =>
          if (optionalA.isDefined) async(es)(callbackC(ab2c(optionalA.get, b)))
          else optionalB = Some(b)
      })
      parA(es) { combinerActor ! Left(_) }
      parB(es) { combinerActor ! Right(_) }
    }

  override def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => callbackB =>
      parA(es) { a => a2pb(a)(es)(callbackB) }

  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      callbackA =>
        async(es)(parA(es)(callbackA))
    }

}
