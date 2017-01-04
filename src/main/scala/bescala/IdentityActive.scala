package bescala

import java.util.concurrent.{ExecutorService, Executors}

import Util.{async}

import scala.language.{higherKinds, implicitConversions}

object IdentityActive extends ActiveCommon {

  override type M[A] = A

  override def fromM[A](ma: M[A]): A = ma

//  override def toM[A](a: => A): M[A] = a

  override def toM[A](a: => A): M[A] = {
    val es: ExecutorService = Executors.newFixedThreadPool(100)
    val ma  = async(es)(a).get()
    es.shutdown()
    ma
  }

//    override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
//      es =>
//        toM(ab2c(fromM(parA(es)), fromM(parB(es))))



  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      def ma: M[A] = parA(es)
      fromM(ma)
    }

//  override def fork[A](parA: => Par[A]): Par[A] =
//    es => {
//      def ma: M[A] = parA(es)
//      async(es)(fromM(ma)).get
//    }

}
