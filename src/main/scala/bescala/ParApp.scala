package bescala

import java.util.concurrent.{ExecutorService, Executors}

import Util._

object ParApp extends App {

  val es: ExecutorService = Executors.newFixedThreadPool(100)

  {
    import Basic._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }

  {
    import Active._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }

  {
    import Reactive._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }

  es.shutdown()

}