package bescala

import bescala.Util._

import scala.language.{higherKinds, implicitConversions}

object Reactive extends Common {

  // C stands for Callback
  type C[A] = A => Unit

  // think of it as a callback invoker
  override type M[A] = C[A] => Unit

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    val ca: A => Unit = a => atomic.setValue(a)
    ma(ca)
    atomic.getValue
  }

  // invoke a callback `ca` by providing it with an `a`
  override def toM[A](a: => A): M[A] =
  ca =>
    ca(a)

  private def toPar[A](ma: M[A]): Par[A] =
    es =>
      ca =>
        async(es)(ma(ca))

  private def toPar[A](a: => A): Par[A] =
    toPar(toM(a))

  override def map[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    es =>
      cb => {
        val ma: M[A] = pa(es)
        val ca: A => Unit =
          a => {
            val pb: Par[B] = toPar(a2b(a))
            val mb: M[B] = pb(es)
            mb(cb)
          }
        ma(ca)
      }

  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      cC => {
        var optionalA: Option[A] = None
        var optionalB: Option[B] = None
        val combinerActor = new Actor[Either[A, B]](es)({
          case Left(a) =>
            if (optionalB.isDefined) {
              val pC: Par[C] = toPar(ab2c(a, optionalB.get))
              val mc: M[C] = pC(es)
              mc(cC)
            }
            else
              optionalA = Some(a)
          case Right(b) =>
            if (optionalA.isDefined) {
              val pC: Par[C] = toPar(ab2c(optionalA.get, b))
              val mc: M[C] = pC(es)
              mc(cC)
            }
            else
              optionalB = Some(b)
        })
        val ma: M[A] = pa(es)
        val ca: A => Unit = a => combinerActor ! Left(a)
        val mb: M[B] = pb(es)
        val cb: B => Unit = b => combinerActor ! Right(b)
        ma(ca)
        mb(cb)
      }

  override def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      cb => {
        val ma: M[A] = pa(es)
        val ca: A => Unit =
          a => {
            val pb: Par[B] = a2pb(a)
            val mb: M[B] = pb(es)
            mb(cb)
          }
        ma(ca)
      }

  override def fork[A](pa: => Par[A]): Par[A] =
    es =>
      ca => {
        val ma = pa(es)
        val p_a: Par[A] = toPar(ma)
        val m_a: M[A] = p_a(es)
        m_a(ca)
      }

}
