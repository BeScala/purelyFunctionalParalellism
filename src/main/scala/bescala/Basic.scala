package bescala

import bescala.Util.async

import scala.language.{higherKinds, implicitConversions}

object Basic extends Common {

  override type M[A] = A

  override def fromM[A](ma: M[A]): A = ma

  override def toM[A](a: => A): M[A] = a

  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      toM(a2b(parA(es)))

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      toM(ab2c(parA(es), parB(es)))

  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      (a2pb(parA(es)))(es)

  override def fork[A](parA: => Par[A]): Par[A] =
    es =>
      async(es)(parA(es)).get

}
