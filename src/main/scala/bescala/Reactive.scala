package bescala

import java.util.concurrent._

import scala.language.{higherKinds, implicitConversions}

import Util.async

object Reactive extends Common {

  override type M[A] = (A => Unit) => Unit

  override def fromM[A](ma: M[A]): A = {
    val ref = new java.util.concurrent.atomic.AtomicReference[A]
    val latch = new CountDownLatch(1)
    ma { a => ref.set(a); latch.countDown }
    latch.await
    ref.get
  }

  override def toM[A](a: => A): M[A] = callback => callback(a)








  override def map[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    es => callbackB =>
      parA(es) { a => async(es)(callbackB(a2b(a))) }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => callbackC => {
      var optionalA: Option[A] = None
      var optionalB: Option[B] = None
      val combiner = new Actor[Either[A, B]] (es) ({
        case Left(a) =>
          if (optionalB.isDefined) async(es)(callbackC(ab2c(a, optionalB.get)))
          else optionalA = Some(a)
        case Right(b) =>
          if (optionalA.isDefined) async(es)(callbackC(ab2c(optionalA.get, b)))
          else optionalB = Some(b)
      })
      parA(es) { a => combiner ! Left(a) }
      parB(es) { b => combiner ! Right(b) }
    }

  override def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => callbackB =>
      parA(es) { a => a2pb(a)(es)(callbackB) }

  override def fork[A](parA: => Par[A]): Par[A] =
    es => callbackA => {
      print("F")
      async(es)(parA(es)(callbackA))
    }

}

/*

    /* Gives us infix syntax for `Par`. */
    // implicit def toParOps[A](p: Par[A]): ParOps[A] = new ParOps(p)

    // infix versions of `map`, `map2` and `flatMap`
    implicit class ParOps[A](p: Par[A]) {
      //      def map[B](f: A => B): Par[B] = map(p)(f)
      //
      //      def map2[B, C](b: Par[B])(f: (A, B) => C): Par[C] = map2(p, b)(f)
      //
      //      def flatMap[B](f: A => Par[B]): Par[B] = flatMap(p)(f)
      //
      //      def zip[B](b: Par[B]): Par[(A, B)] = p.map2(b)((_, _))
    }
  */