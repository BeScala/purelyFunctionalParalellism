package bescala

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import bescala.Util.async

import scala.annotation.tailrec


//              node
//            --------
//   nodeRef |        |
// <---------|        |
//           |        |
//            --------


private class Node[M](var message: M)
  extends AtomicReference[Node[M]]

class Actor[M]
(executorService: ExecutorService)
(messageProcessor: M => Unit) {

  val nullM = null.asInstanceOf[M]

  private val maxNumberOfProcessedMessages = 20

  private val suspended = new AtomicInteger(1)

  //           tail
  //         --------
  //   null |        |
  // <------|  null  |
  //        |        |
  //         --------

  private val tail = new Node[M](nullM)

  //           tail
  //         --------
  //   null |        | tailRef
  // <------|  null  |<--------
  //        |        |
  //         --------

  private val tailRef = new AtomicReference(tail)

  //           tail
  //         --------
  //   null |        | tailRef
  // <------|  null  |<--------
  //        |        |
  //         --------
  //             ^
  //             | headRef
  //             |

  private val headRef = new AtomicReference(tail)

  // assume maxNumberOfProcessedMessages = 2

  // from

  //
  //         --------         --------         --------
  //   null |        |       |        |       |        | tailRef
  // <------|  Msg2  |<------|  Msg1  |<------|  null  |<--------
  //        |        |       |        |       |        |
  //         --------         --------         --------
  //             ^
  //             | headRef
  //             |

  // to

  //
  //         --------         --------
  //   null |        |       |        | tailRef
  // <------|  Msg3  |<------|  null  |<--------
  //        |        |       |        |
  //         --------         --------
  //             ^
  //             | headRef
  //             |

  def !(msg: M): Unit = {

    // from

    //
    //         --------         --------
    //   null |        |       |        | tailRef
    // <------|  Msg1  |<------|  null  |<--------
    //        |        |       |        |
    //         --------         --------
    //             ^
    //             | headRef
    //             |

    // to

    //           node
    //         --------         --------         --------
    //   null |        |       |        |       |        | tailRef
    // <------|  Msg2  |<------|  Msg1  |<------|  null  |<--------
    //        |        |       |        |       |        |
    //         --------         --------         --------
    //             ^
    //             | headRef
    //             |

    def setHeadNode(node: Node[M]): Unit = {
      val oldHeadRef = headRef.getAndSet(node)
      oldHeadRef.lazySet(node)
    }

    setHeadNode(new Node(msg))
    if (suspended.compareAndSet(1, 0)) {
      async(executorService)(act())
    }
  }

  // assume maxNumberOfProcessedMessages = 2

  // from

  //
  //         --------         --------           --------               --------
  //   null |        |       |        |         |        | tailNodeRef |        | tailRef
  // <------|  Msg3  |<------|  Msg2  |<--------|  Msg1  |<------------|  null  |<--------
  //        |        |       |        |         |        |             |        |
  //         --------         --------           --------               --------
  //             ^
  //             | headRef
  //             |

  // to

  //
  //         --------         --------
  //   null |        |       |        | tailRef
  // <------|  Msg3  |<------|  null  |<--------
  //        |        |       |        |
  //         --------         --------
  //             ^
  //             | headRef
  //             |

  private def act(): Unit = {
    // from

    //                              node
    //         --------           --------           --------               --------
    //   null |        |         |        |         |        |             |        | tailRef
    // <------|  Msg3  |<--------|  Msg2  |<--------|  Msg1  |<------------|  null  |<--------
    //        |        |         |        |         |        |             |        |
    //         --------           --------           --------               --------
    //             ^
    //             | headRef
    //             |

    // to

    //
    //         --------         --------
    //   null |        |       |        | tailRef
    // <------|  Msg3  |<------|  null  |<--------
    //        |        |       |        |
    //         --------         --------
    //             ^
    //             | headRef
    //             |

    def setTailNode(node: Node[M]): Unit = {
      node.message = nullM
      tailRef.lazySet(node)
    }

    val tailNodeRef = tailRef.get

    // from

    //
    //         --------           --------           --------               --------
    //   null |        | nodeRef |        |         |        | tailRef.get |        | tailRef
    // <------|  Msg3  |<--------|  Msg2  |<--------|  Msg1  |<------------|  null  |<--------
    //        |        |         |        |         |        |             |        |
    //         --------           --------           --------               --------
    //             ^
    //             | headRef
    //             |

    // to

    //
    //         --------         --------
    //   null |        |       |        | tailRef
    // <------|  Msg3  |<------|  null  |<--------
    //        |        |       |        |
    //         --------         --------
    //             ^
    //             | headRef
    //             |

    def setTailRef(nodeRef: Node[M]): Unit = {
      if (nodeRef ne tailRef.get) {
        setTailNode(nodeRef)
        async(executorService)(act())
      } else {
        suspended.set(1)
        if ((nodeRef.get ne null) && suspended.compareAndSet(1, 0)) {
          async(executorService)(act())
        }
      }
    }

    setTailRef(processMessages(tailRef.get, maxNumberOfProcessedMessages))
  }


  // from (assume maxNumberOfProcessedMessages = 2)

  //
  //         --------         --------           --------               --------
  //   null |        |       |        |   ref   |        |   nodeRef   |        | tailRef
  // <------|  Msg3  |<------|  Msg2  |<--------|  Msg1  |<------------|  null  |<--------
  //        |        |       |        |         |        |             |        |
  //         --------         --------           --------               --------
  //             ^
  //             | headRef
  //             |

  // to

  //
  //         --------           --------           --------               --------
  //   null |        |   ref   |        |         |        |   nodeRef   |        | tailRef
  // <------|  Msg3  |<--------|  Msg2  |<--------|  Msg1  |<------------|  null  |<--------
  //        |        |         |        |         |        |             |        |
  //         --------           --------           --------               --------
  //             ^
  //             | headRef
  //             |

  @tailrec
  private def processMessages(nodeRef: Node[M], i: Int): Node[M] = {
    val ref = nodeRef.get
    if (ref ne null) {
      val message = ref.message
      messageProcessor.apply(message)
      if (i > 0) processMessages(ref, i - 1) else ref
    } else {
      nodeRef
    }
  }

}
