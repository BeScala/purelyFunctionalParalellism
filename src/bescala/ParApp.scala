package bescala

import java.util.concurrent.{ExecutorService, Executors}


/**
  * Created by lucd on 12/14/16.
  */
object ParApp extends App {

  val es: ExecutorService = Executors.newFixedThreadPool(20)

  def foo[A](a: => A): A = {
    (1 to 10) foreach { _ =>
      Thread.sleep(scala.math.ceil(scala.math.random * 1000).toLong)
      print(Thread.currentThread().getId)
    }
    a
  }


  {

    import Reactive._

    val bar = map2[Int, Int, Int](unit(foo[Int](1)), unit(foo[Int](1)))(_ + _)
    val forkedBar = forkedMap2[Int, Int, Int](unit(foo[Int](1)), unit(foo[Int](1)))(_ + _)


    println(run(es)(bar))


  }



}
