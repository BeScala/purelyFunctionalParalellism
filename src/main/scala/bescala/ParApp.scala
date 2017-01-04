package bescala

import java.util.concurrent.{ExecutorService, Executors}

import Util._

object ParApp extends App {

  val es: ExecutorService = Executors.newFixedThreadPool(100)

    {
      import Reactive._

      println("\nReactive")

      def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))
      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
      def verboseForkedUnit[A](a: => A): Par[A] = forkedUnit(verbose(a))

      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")

    }

    {
      import FutureActive._

      println("\nFuture Active")

      def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))
      def verboseForkedUnit[A](a: => A): Par[A] = forkedUnit(verbose(a))

      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")

    }

    {
      import IdentityActive._

      println("\nIdentity Active")

      def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))
      def verboseForkedUnit[A](a: => A): Par[A] = forkedUnit(verbose(a))

      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")
      println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseForkedUnit(_))))(_.sum))}")

    }


  es.shutdown()

}