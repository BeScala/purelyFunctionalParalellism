package bescala

import java.util.concurrent._
import java.util.concurrent.atomic._

import scala.annotation.tailrec

object Util {

  //
  // `async` utility function
  //

  def async[A](es: ExecutorService)(a: => A): Future[A] =
    es.submit(new Callable[A] {
      def call = a
    })


  //
  // `Atomic` utility class
  //

  class Atomic[Z](ref: AtomicReference[Z] = new AtomicReference[Z]) {
    val countDownLatch: CountDownLatch = new CountDownLatch(1)

    def setValue(z: Z) {
      this.ref.set(z)
      countDownLatch.countDown()
    }

    def getValue = {
      countDownLatch.await
      this.ref.get
    }
  }

  //
  // kind of tricky `when` utility function: use VERY carefully
  //

  @tailrec
  def when[A](cond: => Boolean)(a: => A): A =
    if(cond) a
    else when(cond)(a)

  def whenDefined[A](optionalA: => Option[A]): A =
    when(optionalA != None)(optionalA.get)

  //
  // utilities that can be used to show stuff
  //

  def randomVerboseSleep(time: Int): Unit = {
    Thread.sleep(scala.math.ceil(scala.math.random * time).toLong)
  }

  def verbose[A](blockA: => A): A = {
    val a = blockA
    (0 to 9) foreach { _ =>
      randomVerboseSleep(50)
      print(s"${a}")
    }
    a
  }
}
