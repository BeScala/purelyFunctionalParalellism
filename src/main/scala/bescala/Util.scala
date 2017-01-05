package bescala

import java.util.concurrent._
import java.util.concurrent.atomic._

import scala.math._

object Util {

  //
  // `async` utility function
  //

  def async[A](es: ExecutorService)(a: => A): Future[A] =
    es.submit(new Callable[A] {
      def call = {
        print("."); a
      }
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
  // utilities that can be used to show stuff
  //

  private def randomVerboseSleep(time: Int): Unit = {
    Thread.sleep(ceil(random * time).toLong)
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
