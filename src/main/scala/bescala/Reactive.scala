package bescala

import bescala.Util._

import scala.language.{higherKinds, implicitConversions}

object Reactive extends Common {

  override type M[A] = (A => Unit) => Unit

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    val callbackA: A => Unit = a => atomic.setValue(a)
    ma(callbackA)
    atomic.getValue
  }

  override def toM[A](a: => A): M[A] =
    callbackA =>
      callbackA(a)

  private def toPar[A](ma: M[A]): Par[A] =
    es =>
      callbackA =>
        async(es)(ma(callbackA))

  private def toPar[A](a: => A): Par[A] =
    toPar(toM(a))

  override def map[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      callbackB => {
        val ma: M[A] = parA(es)
        val callbackA: A => Unit =
          a => {
            val parB: Par[B] = toPar(a2b(a))
            val mb: M[B] = parB(es)
            mb(callbackB)
          }
        // val callbackA: A => Unit = a => async(es)(callbackB(a2b(a)))
        ma(callbackA)
      }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      callbackC => {
        var optionalA: Option[A] = None
        var optionalB: Option[B] = None
        val combinerActor = new Actor[Either[A, B]](es)({
          case Left(a) =>
            if (optionalB.isDefined) {
              val parC: Par[C] = toPar(ab2c(a, optionalB.get))
              val mc: M[C] = parC(es)
              mc(callbackC)
            }
            // async(es)(callbackC(ab2c(a, optionalB.get)))
            else
              optionalA = Some(a)
          case Right(b) =>
            if (optionalA.isDefined) {
              val parC: Par[C] = toPar(ab2c(optionalA.get, b))
              val mc: M[C] = parC(es)
              mc(callbackC)
            }
            // async(es)(callbackC(ab2c(optionalA.get, b)))
            else
              optionalB = Some(b)
        })
        val ma: M[A] = parA(es)
        val callbackA: A => Unit = a => combinerActor ! Left(a)
        val mb: M[B] = parB(es)
        val callbackB: B => Unit = b => combinerActor ! Right(b)
        ma(callbackA)
        mb(callbackB)
      }

  override def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      callbackB => {
        val ma: M[A] = parA(es)
        val callbackA: A => Unit =
          a => {
            val parB: Par[B] = a2pb(a)
            val mb: M[B] = parB(es)
            mb(callbackB)
          }
        // a2pb(a)(es)(callbackB)
        ma(callbackA)
      }

  override def fork[A](parA: => Par[A]): Par[A] =
    es =>
      callbackA => {
        val ma = parA(es)
        val par_a: Par[A] = toPar(ma)
        val m_a: M[A] = par_a(es)
        m_a(callbackA)
        // async(es)(ma(callbackA))
      }

}
