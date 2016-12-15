package bescala

import java.util.concurrent._

import language.{higherKinds, implicitConversions}

import Util.async

object Basic extends Common {

  override type M[A] = A

  override def fromM[A](ma: M[A]): A = ma







  override def toM[A](a: => A): M[A] = a



  override def run[A](es: ExecutorService)(parA: => Par[A]): A = async(es)(parA(es)).get




  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      a2b(run(es)(parA))

  override def map2[A,B,C](parA: Par[A], parB: Par[B])(ab2c: (A,B) => C): Par[C] =
    es =>
      ab2c(run(es)(parA), run(es)(parB))













  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      (a2pb(run(es)(parA)))(es)

  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      print("F")
      run(es)(parA)
    }
}
