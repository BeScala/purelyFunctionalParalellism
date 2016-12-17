## Purely Functional Parallelism

### Utilities

#### `async`

No *paralellism* without being able to execute code *asynchronously*

```
  def async[A](es: ExecutorService)(a: => A): Future[A] =
    es.submit(new Callable[A] {
      def call = a
    })
```

#### `Atomic`

Likewise, when dealing with *mutability*,
*atomicity* is an important ingredient of paralellism 

```
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
```

#### `verbose`

To see what is happening we define the following `verbose` function

```
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
```

#### Common code

A lot of *common* code can be written in terms of an abstract type

```
  type M[A]

```

It is convenient to define `fromM` and `toM` abstract functions

```
   def fromM[A](ma: M[A]): A

   def toM[A](a: => A): M[A]
```

The concrete type for dealing with parallelism is defined as

```
  type Par[A] = ExecutorService => M[A]
```

Using `fromM` and `toM` we can already define two concrete functions
`unit` and `run` to *get into* and *get out of* the `Par[_]` world

```
  def unit[A](a: => A): Par[A] =
    es =>
      toM(a)

  def run[A](es: ExecutorService)(parA: => Par[A]): A =
    fromM(parA(es))
```

The basic *applicative* and *monadic* `Par[_]` features are

```
  def map[A, B](parA: Par[A])(a2b: A => B): Par[B]

  def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C]

  def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B]
```

The one and only extra `Par[_]` features is

```
def fork[A](parA: => Par[A]): Par[A]
```

`fork`, as it's name suggests, *forks* a parallel computation 

Using the functions defined so far a lot of other ones can be defined

```
  def join[A](parParA: Par[Par[A]]): Par[A] =
    flatMap(parParA)(x => x)

  def choice[A](parBoolean: Par[Boolean])(trueParA: Par[A], falseParA: Par[A]): Par[A] =
    flatMap(parBoolean)(b => if (b) trueParA else falseParA)

  def choiceN[A](parInt: Par[Int])(parAs: List[Par[A]]): Par[A] =
    flatMap(parInt)(parAs(_))

  def choiceMap[K,V](parK: Par[K])(choices: Map[K, Par[V]]): Par[V] =
    flatMap(parK)(choices)

  def sequence[A](parAs: List[Par[A]]): Par[List[A]] =
    parAs.foldRight[Par[List[A]]](unit(List()))(map2(_, _)(_ :: _))

  def mapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    sequence(as.map(forkedFunction(a2b)))

  def filterList[A](as: List[A])(predicateA: A => Boolean): Par[List[A]] =
    map(sequence(as map (a => unit(if (predicateA(a)) List(a) else List()))))(_.flatten)
```

as wel as their forked equivalents

```
  def forkedUnit[A](a: => A): Par[A] =
    fork(unit(a))

  def forkedMap[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    fork(map(fork(parA))(a2b))

  def forkedMap2[A,B,C](parA: Par[A], parB: Par[B])(ab2c: (A,B) => C): Par[C] =
    fork(map2(fork(parA), fork(parB))(ab2c))

  def forkedSequence[A](parAs: List[Par[A]]): Par[List[A]] = fork {
    if (parAs.isEmpty) unit(List())
    else if (parAs.length == 1) forkedMap(parAs.head)(List(_))
    else {
      val (lparAs, rparAs) = parAs.splitAt(parAs.length/2)
      forkedMap2(forkedSequence(lparAs), forkedSequence(rparAs))(_ ++ _)
    }
  }

  def forkedFunction[A, B](a2b: A => B): A => Par[B] =
    a => forkedUnit(a2b(a))

  def forkedMapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    forkedSequence(as.map(forkedFunction(a2b)))

  def forkedFilterList[A](as: List[A])(predicateA: A => Boolean): Par[List[A]] =
    forkedMap(forkedSequence(as map (a => forkedUnit(if (predicateA(a)) List(a) else List()))))(_.flatten)
```

#### Basic

The simplest choice for `M[A]` is `A` leading to

```
object Basic extends Common {

  override type M[A] = A

  override def fromM[A](ma: M[A]): A = ma

  override def toM[A](a: => A): M[A] = a

  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      toM(a2b(parA(es)))

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      toM(ab2c(parA(es), parB(es)))

  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      (a2pb(parA(es)))(es)

  override def fork[A](parA: => Par[A]): Par[A] =
    es =>
      async(es)(parA(es)).get

}
```

evaluating 

```
  {
    import Basic._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }
```

results in

```
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
```

not a lot of paralellism happening

#### Active

The next logical choice for `M[A]` is `Future[A]` (note: *java* future) leading to

```
object Active extends Common {

  override type M[A] = Future[A]

  override def fromM[A](ma: M[A]): A = ma.get

  override def toM[A](a: => A): M[A] =
    new M[A] {
      def get = a
      def get(timeout: Long, units: TimeUnit) = get
      def isDone = true
      def isCancelled = false
      def cancel(evenIfRunning: Boolean): Boolean = false
    }

  override def map[A,B](parA: Par[A])(a2b: A => B): Par[B] =
    es =>
      toM(a2b(parA(es).get))

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      toM(ab2c(parA(es).get, parB(es).get))

  override def flatMap[A,B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      (a2pb(parA(es).get))(es)

  override def fork[A](parA: => Par[A]): Par[A] =
    es =>
      async(es)(parA(es).get)

}
```

Note that `fromM` is rather simple while `toM` is more complex

evaluating 

```
  {
    import Active._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }
```

results in

```
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
```

again, not a lot of paralellism happening


#### Actor

How can we improve the `Active` code?

The natural candidate is `map2` which should, somehow, avoid *blocking*

The code below is a minimal implementation of the idea of an *actor*:
it is possible to *send a message to an actor* in a *non-blocking* way

```
private class Node[M](var message: M)
  extends AtomicReference[Node[M]]

class Actor[M]
(executorService: ExecutorService)
(messageProcessor: M => Unit) {

  val nullM = null.asInstanceOf[M]

  private val maxNumberOfProcessedMessages = 2

  private val suspended = new AtomicInteger(1)

  private val headRef = new AtomicReference(tail)

  def !(msg: M): Unit = {

    def setHeadNode(node: Node[M]): Unit = {
      val oldHeadRef = headRef.getAndSet(node)
      oldHeadRef.lazySet(node)
    }

    setHeadNode(new Node(msg))
    if (suspended.compareAndSet(1, 0)) {
      async(executorService)(act())
    }
  }

  private def act(): Unit = {

    def setTailNode(node: Node[M]): Unit = {
      node.message = nullM
      tailRef.lazySet(node)
    }

    val tailNodeRef = tailRef.get

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
```

A `Node[M]` instance both 
holds a *message* of type `M`
and 
is an atomic reference to a `Node[M]`
as such
implementing a *queue of messages*

An actor *acts* by *asynchronously processing an amount of messages* 
of its queue

The `map2` code can now be defined in terms of `Actor` as follows

```
  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => {
      var optionalMa: Option[M[A]] = None
      var optionalMb: Option[M[B]] = None
      var optionalMc: Option[M[C]] = None
      val combinerActor = new Actor[Either[M[A], M[B]]](es)({
        case Left(ma) =>
          if (optionalMb.isDefined) optionalMc = Some(toM(ab2c(ma.get, optionalMb.get.get)))
          else optionalMa = Some(ma)
        case Right(mb) =>
          if (optionalMa.isDefined) optionalMc = Some(toM(ab2c(optionalMa.get.get, mb.get)))
          else optionalMb = Some(mb)
      })
      combinerActor ! Left(parA(es))
      combinerActor ! Right(parB(es))

      whenDefined(optionalMc)
    }
```

The `combinerActor`'s `messageProcessor` processes 
*either an* `ma` *or an* `mb` and when both are received, 
it combines `ma` *and* `mb` to produce an `mc`, effectively 
consuming a *sum* and 
producing a value depending on a *product*
 
evaluating 

```
  {
    import Active._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }
```

results in

```
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
7950423847631927692805467305198926371209451862163072598479513796050274086445826135397838435016201841
result: 45
```

finally we see some paralellism happening

But what is this expression `whenDefined(optionalMc)` all about?

There is some race condition happening here: 
`optionalMc.get` might fail because `optionalMc` might still be `None`

Therefore we used the somewhat *risky to use* function `whenDefined`
(there may be a better way to deal with this)

```
  @tailrec
  def when[A](cond: => Boolean)(a: => A): A =
    if(cond) a
    else when(cond)(a)

  def whenDefined[A](optionalA: => Option[A]): A =
    when(optionalA != None)(optionalA.get)
```

#### Reactive

The best choice for `M[A]` is probably `(A => Unit) => Unit`

Think of `A => Unit` as a *callback* and
think of `(A => Unit) => Unit` as a *callback handler*
handling callbacks *asynchronously*

```
object Reactive extends Common {

  override type M[A] = (A => Unit) => Unit

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    ma(atomic.setValue(_))
    atomic.getValue
  }

  override def toM[A](a: => A): M[A] =
    callback =>
      callback(a)

  override def map[A, B](parA: Par[A])(a2b: A => B): Par[B] =
    es => callbackB =>
      parA(es) { a => async(es)(callbackB(a2b(a))) }

  override def map2[A, B, C](parA: Par[A], parB: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => callbackC => {
      var optionalA: Option[A] = None
      var optionalB: Option[B] = None
      val combinerActor = new Actor[Either[A, B]](es)({
        case Left(a) =>
          if (optionalB.isDefined) async(es)(callbackC(ab2c(a, optionalB.get)))
          else optionalA = Some(a)
        case Right(b) =>
          if (optionalA.isDefined) async(es)(callbackC(ab2c(optionalA.get, b)))
          else optionalB = Some(b)
      })
      parA(es) { combinerActor ! Left(_) }
      parB(es) { combinerActor ! Right(_) }
    }

  override def flatMap[A, B](parA: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => callbackB =>
      parA(es) { a => a2pb(a)(es)(callbackB) }

  override def fork[A](parA: => Par[A]): Par[A] =
    es => {
      callbackA =>
        async(es)(parA(es)(callbackA))
    }

}
```
Note that `toM` is rather simple while `fromM` is more complex

evaluating 

```
  {
    import Reactive._

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 9).toList.map(verboseUnit(_))))(_.sum))}")

  }
```

results in

```
0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999
result: 45
8309475108623565179740989329587648179592031046604198783668052776455130942336801285740539316271142422
result: 45
```

again we see some paralellism happening 
(and this tine without the tricky `whenDefined` function)



