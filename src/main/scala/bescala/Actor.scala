package bescala

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import bescala.Util.async

private class Node[A](var value: Option[A]) extends AtomicReference[Node[A]]

class Actor[A](val es: ExecutorService)(callbackA: A => Unit) {

  private val tail = new AtomicReference(new Node[A](None))
  private val head = new AtomicReference(tail.get)

  private def enqueue(n: Node[A]): Unit = head.getAndSet(n).lazySet(n)

  private def dequeue: Node[A] = tail.get.get

  private def endOfQueue(n: Node[A]): Boolean = n ne null

  private val suspended = new AtomicInteger(1)

  private def schedule(): Unit =
    async(es) {
      val node = dequeue
      if (endOfQueue(node)) {
        callbackA(node.value.get)
        node.value = None
        tail.lazySet(node)
        schedule()
      } else {
        suspended.set(1)
        if (endOfQueue(node) && suspended.compareAndSet(1, 0)) schedule()
      }
    }

  def !(a: A): Unit = {
    enqueue(new Node(Some(a)))
    if (suspended.compareAndSet(1, 0)) schedule()
  }

}
