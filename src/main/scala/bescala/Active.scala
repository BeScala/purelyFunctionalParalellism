package bescala

import java.util.concurrent._

import language.{higherKinds, implicitConversions}

import Util.async

object Active extends Common {

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

  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      toM(a2b(parA(es).get))

  override def map2[A,B,C](parA: Par[A], parB: Par[B])(ab2c: (A,B) => C): Par[C] =
    es =>
      toM(ab2c(parA(es).get, parB(es).get))














  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      (a2pb(parA(es).get))(es)

  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      print("F")
      async(es)(parA(es).get)
    }

}


