package bescala

/**
  * Created by lduponch on 13/12/2016.
  */

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{Callable, ExecutorService}

import scala.annotation.tailrec

final class Actor[A]
(es: ExecutorService)
(onSuccess: A => Unit, onFailure: Throwable => Unit = throw (_)) {
  self =>

  private val tail = new AtomicReference(new Node[A]())
  private val head = new AtomicReference(tail.get)

  private val suspended = new AtomicInteger(1)

  def !(a: A) {
    val n = new Node(a)
    head.getAndSet(n).lazySet(n)
    trySchedule()
  }

  private def trySchedule() =
    if (suspended.compareAndSet(1, 0)) schedule()

  private def schedule() {
    () => es.submit {
      new Callable[Unit] {
        def call = act()
      }
    }.get
  }

  private def act() {
    val t = tail.get
    val n = batchHandle(t, 1024)
    if (n ne t) {
      n.value = null.asInstanceOf[A]
      tail.lazySet(n)
      schedule()
    } else {
      suspended.set(1)
      if (n.get ne null) trySchedule()
    }
  }

  @tailrec
  private def batchHandle(t: Node[A], i: Int): Node[A] = {
    val n = t.get
    if (n ne null) {
      try {
        onSuccess(n.value)
      } catch {
        case ex: Throwable => onFailure(ex)
      }
      if (i > 0) batchHandle(n, i - 1) else n
    } else t
  }
}

private class Node[A](var value: A = null.asInstanceOf[A]) extends AtomicReference[Node[A]]

object AsyncActor {

  def apply[A](es: ExecutorService)(onSuccess: A => Unit, onFailure: Throwable => Unit = throw (_)): Actor[A] =
    new Actor(es)(onSuccess, onFailure)

  def choice[A, B](es: ExecutorService)(left: A => Unit, right: B => Unit) = AsyncActor[Either[A, B]](es) {
    case Left(a) => left(a)
    case Right(b) => right(b)
  }
}