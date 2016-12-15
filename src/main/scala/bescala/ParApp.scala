package bescala

import java.util.concurrent.{ExecutorService, Executors}

object ParAppUtils {

  val es: ExecutorService = Executors.newFixedThreadPool(100)

  def randomVerboseSleep(time: Int): Unit = {
    Thread.sleep(scala.math.ceil(scala.math.random * time).toLong)
  }

  def verbose[A](blockA: => A): A = {
    val a = blockA
    (1 to 10) foreach { _ =>
      randomVerboseSleep(50)
      print(s"${a}")
    }
    a
  }

}

object ParApp extends App {

  import ParAppUtils._

  // import Basic._
  // import Active._
  import Reactive._

  def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

  // println(s"\n${run(es)(map(sequence((1 to 9).toList.map(verboseUnit(_))))(_.sum))}")
  println(s"\nresult: ${run(es)(forkedMap(forkedSequence((1 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  es.shutdown()

}