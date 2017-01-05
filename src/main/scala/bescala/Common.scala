package bescala

import java.util.concurrent.ExecutorService

import scala.language.{higherKinds, implicitConversions}

trait Common {

  //
  // abstract type
  //

  type M[A]

  //
  // concrete type
  //

  type Par[A] = ExecutorService => M[A]

  //
  // abstract M[A] related
  //

  def fromM[A](ma: M[A]): A

  def toM[A](a: => A): M[A]

  //
  // abstract applicative and monadic Par[A] computational features
  //

  def map[A, B](pa: Par[A])(a2b: A => B): Par[B]

  def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C]

  def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B]

  //
  //  only extra abstract Par[A] computational feature
  //

  def fork[A](pa: => Par[A]): Par[A]

  //
  // concrete
  //

  // monadic unit in terms of toM
  def unit[A](a: => A): Par[A] =
  es =>
    toM(a)

  // monadic run in terms of fromM
  def run[A](es: ExecutorService)(pa: => Par[A]): A =
  fromM(pa(es))

  //
  // more concrete stuff
  //

  def join[A](ppa: Par[Par[A]]): Par[A] =
    flatMap(ppa)(identity)

  def choice[A](pb: Par[Boolean])(tpa: Par[A], fpa: Par[A]): Par[A] =
    flatMap(pb)(b => if (b) tpa else fpa)

  def choiceN[A](pi: Par[Int])(pas: List[Par[A]]): Par[A] =
    flatMap(pi)(pas(_))

  def choiceMap[K, V](pk: Par[K])(choices: Map[K, Par[V]]): Par[V] =
    flatMap(pk)(choices)

  // quadratic instead of linear `pas.foldRight[Par[List[A]]](unit(List()))(map2(_, _)(_ :: _))`
  def sequence[A](pas: List[Par[A]]): Par[List[A]] = {
    if (pas.isEmpty) unit(List())
    else if (pas.length == 1) map(pas.head)(List(_))
    else {
      val (lpas, rpas) = pas.splitAt(pas.length / 2)
      val plas = sequence(lpas)
      val pras = sequence(rpas)
      map2(plas, pras)(_ ++ _)
    }
  }

  def mapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    sequence(as.map(forkedFunction(a2b)))

  def filterList[A](as: List[A])(a2b: A => Boolean): Par[List[A]] =
    map(sequence(as map (a => unit(if (a2b(a)) List(a) else List()))))(_.flatten)

  //
  // some forked versions
  //

  def forkedUnit[A](a: => A): Par[A] =
    fork(unit(a))

  def forkedMap[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    map(fork(pa))(a2b)

  def forkedMap2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    map2(fork(pa), fork(pb))(ab2c)

  // also quadratic
  def forkedSequence[A](pas: List[Par[A]]): Par[List[A]] = {
    if (pas.isEmpty) unit(List())
    else if (pas.length == 1) forkedMap(pas.head)(List(_))
    else {
      val (lpas, rpas) = pas.splitAt(pas.length / 2)
      forkedMap2(forkedSequence(lpas), forkedSequence(rpas))(_ ++ _)
    }
  }

  def forkedFunction[A, B](a2b: A => B): A => Par[B] =
    a =>
      forkedUnit(a2b(a))

  def forkedMapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    forkedSequence(as.map(forkedFunction(a2b)))

  def forkedFilterList[A](as: List[A])(a2b: A => Boolean): Par[List[A]] =
    forkedMap(forkedSequence(as map (a => forkedUnit(if (a2b(a)) List(a) else List()))))(_.flatten)

  //
  // example: sorting
  //

  def sort[A: Ordering](pas: Par[List[A]]) = map(pas)(_.sorted)

  def forkedSort[A: Ordering](pas: Par[List[A]]) = forkedMap(pas)(_.sorted)

}
