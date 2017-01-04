package bescala

import Util.{Atomic}

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
      val ma = parA(es)
      val mb = parB(es)
      toM(ab2c(fromM(ma), fromM(mb)))
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