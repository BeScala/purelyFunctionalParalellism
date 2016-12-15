package bescala

import java.util.concurrent.{Callable, ExecutorService, Future}

object Util {

  //
  // Future Utility
  //

  def async[A](es: ExecutorService)(a: => A): Future[A] =
    es.submit(new Callable[A] {
      def call = a
    })

}
