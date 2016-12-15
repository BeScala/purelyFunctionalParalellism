package bescala

import java.util.concurrent.ExecutorService

import language.{higherKinds, implicitConversions}

import Util.async

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
  // abstract applicative and monad features
  //

  def map[A, B](parA: Par[A])(a2b: A => B): Par[B]

  def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C]

  def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B]

  //
  //  only extra abstract feature
  //

  def fork[A](parA: => Par[A]): Par[A]

  //
  // concrete
  //

  def run[A](es: ExecutorService)(parA: => Par[A]): A = fromM(parA(es))

  def unit[A](a: => A): Par[A] = es => toM(a)

  def join[A](parParA: Par[Par[A]]): Par[A] =
    flatMap(parParA)(x => x)

  def choice[A](parBoolean: Par[Boolean])(trueParA: Par[A], falseParA: Par[A]): Par[A] =
    flatMap(parBoolean)(b => if (b) trueParA else falseParA)

  def choiceN[A](parInt: Par[Int])(parAs: List[Par[A]]): Par[A] =
    flatMap(parInt)(parAs(_))

  def choiceMap[K,V](parK: Par[K])(choices: Map[K, Par[V]]): Par[V] =
    flatMap(parK)(choices)

  def sequence[A](parAs: List[Par[A]]): Par[List[A]] =
    parAs.foldRight[Par[List[A]]](unit(List()))(map2(_, _)(_ :: _))

  def mapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    sequence(as.map(forkedFunction(a2b)))

  def filterList[A](as: List[A])(predicateA: A => Boolean): Par[List[A]] =
    map(sequence(as map (a => unit(if (predicateA(a)) List(a) else List()))))(_.flatten)

  //
  // forked versions
  //

  def forkedUnit[A](a: => A): Par[A] =
    fork(unit(a))

  def forkedMap[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    fork(map(fork(parA))(a2b))

  def forkedMap2[A,B,C](parA: Par[A], parB: Par[B])(ab2c: (A,B) => C): Par[C] =
    fork(map2(fork(parA), fork(parB))(ab2c))

  def forkedSequence[A](parAs: List[Par[A]]): Par[List[A]] = fork {
    if (parAs.isEmpty) unit(List())
    else if (parAs.length == 1) forkedMap(parAs.head)(List(_))
    else {
      val (lpas, rpas) = parAs.splitAt(parAs.length/2)
      forkedMap2(forkedSequence(lpas), forkedSequence(rpas))(_ ++ _)
    }
  }

  def forkedFunction[A, B](a2b: A => B): A => Par[B] =
    a => forkedUnit(a2b(a))

  def forkedMapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    forkedSequence(as.map(forkedFunction(a2b)))

  def forkedFilterList[A](as: List[A])(predicateA: A => Boolean): Par[List[A]] =
    forkedMap(forkedSequence(as map (a => forkedUnit(if (predicateA(a)) List(a) else List()))))(_.flatten)

  //
  // example: sorting
  //

  def sort[A : Ordering](parAs: Par[List[A]]) = map(parAs)(_.sorted)

  def forkedSort[A : Ordering](parAs: Par[List[A]]) = forkedMap(parAs)(_.sorted)

}
