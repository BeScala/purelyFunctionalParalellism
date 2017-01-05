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

In order to see what is happening we're actually going to make use of a more verbose version

```
  def async[A](es: ExecutorService)(a: => A): Future[A] =
    es.submit(new Callable[A] {
      def call = { print(".") ; a }
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

  def run[A](es: ExecutorService)(pa: => Par[A]): A =
  fromM(pa(es))
```

The basic *applicative* and *monadic* `Par[_]` features are

```
  def map[A, B](pa: Par[A])(a2b: A => B): Par[B]

  def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C]

  def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B]
```

The one and only extra `Par[_]` feature is

```
  def fork[A](pa: => Par[A]): Par[A]
```

`fork`, as it's name suggests, *forks* a parallel computation 
and you can sprinkle your code with `fork`'s wherever you like

Using the functions defined so far a lot of other ones can be defined

```
  def join[A](ppa: Par[Par[A]]): Par[A] =
    flatMap(ppa)(identity)

  def choice[A](pb: Par[Boolean])(tpa: Par[A], fpa: Par[A]): Par[A] =
    flatMap(pb)(b => if (b) tpa else fpa)

  def choiceN[A](pi: Par[Int])(pas: List[Par[A]]): Par[A] =
    flatMap(pi)(pas(_))

  def choiceMap[K, V](pk: Par[K])(choices: Map[K, Par[V]]): Par[V] =
    flatMap(pk)(choices)

  def sequence[A](pas: List[Par[A]]): Par[List[A]] = {
    if (pas.isEmpty) unit(List())
    else if (pas.length == 1) map(pas.head)(List(_))
    else {
      val (lpas, rpas) = pas.splitAt(pas.length / 2)
      val plas = sequence(lpas)
      val pras = sequence(rpas)
      map2(plas, pras)(_ ++ _)
    }
  }

  def mapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    sequence(as.map(forkedFunction(a2b)))

  def filterList[A](as: List[A])(a2b: A => Boolean): Par[List[A]] =
    map(sequence(as map (a => unit(if (a2b(a)) List(a) else List()))))(_.flatten)
```

as wel as some forked equivalents

```
  def forkedUnit[A](a: => A): Par[A] =
    fork(unit(a))

  def forkedMap[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    map(fork(pa))(a2b)

  def forkedMap2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    map2(fork(pa), fork(pb))(ab2c)

  def forkedSequence[A](pas: List[Par[A]]): Par[List[A]] = {
    if (pas.isEmpty) unit(List())
    else if (pas.length == 1) forkedMap(pas.head)(List(_))
    else {
      val (lpas, rpas) = pas.splitAt(pas.length / 2)
      forkedMap2(forkedSequence(lpas), forkedSequence(rpas))(_ ++ _)
    }
  }

  def forkedFunction[A, B](a2b: A => B): A => Par[B] =
    a =>
      forkedUnit(a2b(a))

  def forkedMapList[A, B](as: List[A])(a2b: A => B): Par[List[B]] =
    forkedSequence(as.map(forkedFunction(a2b)))

  def forkedFilterList[A](as: List[A])(a2b: A => Boolean): Par[List[A]] =
    forkedMap(forkedSequence(as map (a => forkedUnit(if (a2b(a)) List(a) else List()))))(_.flatten)
```

Note that we have not used the standard *linear* definition 
of `sequence` (`pas.foldRight[Par[List[A]]](unit(List()))(map2(_, _)(_ :: _))`)
but used a *quadratic* definition instead


#### Active

A first choice for `M[A]` is `Future[A]` (note: *java* future) leading to

```
object Active extends Common {

  override type M[A] = java.util.concurrent.Future[A]

  override def fromM[A](ma: M[A]): A = ma.get

  // we do not care to much about timeout
  override def toM[A](a: => A): M[A] = new M[A] {
    def get = a

    def get(timeout: Long, units: TimeUnit) = get

    def isDone = true

    def isCancelled = false

    def cancel(evenIfRunning: Boolean): Boolean = false
  }

  override def map[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    es => {
      val ma: M[A] = pa(es)
      toM(a2b(fromM(ma)))
    }

  // it is *very* important to define `ma` and `mb` outside of `toM(ab2c(fromM(ma), fromM(mb)))`
  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
  es => {
    val ma = pa(es)
    val mb = pb(es)
    toM(ab2c(fromM(ma), fromM(mb)))
  }

  override def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B] =
    es => {
      val ma: M[A] = pa(es)
      a2pb(fromM(ma))(es)
    }

  override def fork[A](pa: => Par[A]): Par[A] =
    es => {
      val ma: M[A] = pa(es)
      async(es)(fromM(ma))
    }

}
```

Note that `fromM` is rather simple while `toM` is more complex

Also note that `fromM` is *actively blocking*

Finally, note that, for *map2* it is *very* important to define `ma` and `mb` *outside* of `toM(ab2c(fromM(ma), fromM(mb)))` (thanks, Peter!)

consider the following block of code

```
  {
    import Active._

    println("\nActive")

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))
    def verboseForkedUnit[A](a: => A): Par[A] = forkedUnit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 5).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(map(sequence((0 to 5).toList.map(verboseForkedUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 5).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 5).toList.map(verboseForkedUnit(_))))(_.sum))}")

  }
```

evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(100)` results in

```
Active
000000000011111111112222222222333333333344444444445555555555
result: 15
......134215053002345041254130323514305424021543015254235010213412
result: 15
.................231541320300514102523001453220435102532425140523540134315441
result: 15
.......................451102435343254021340215234031435122110413052043505135224500
result: 15
```

evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(1)` results in

```
Active
000000000011111111112222222222333333333344444444445555555555
result: 15
.0000000000.1111111111.2222222222.3333333333.4444444444.5555555555
result: 15
.0000000000..1111111111..2222222222....3333333333..4444444444..5555555555....
result: 15
.0000000000...1111111111...2222222222.....3333333333...4444444444...5555555555.....
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(2)` results in

```
Active
000000000011111111112222222222333333333344444444445555555555
result: 15
..0100110110010110011.0.2223232232223332.4334343.55454454545454555
result: 15
..0000000000..1111111111..2222222222....3333333333..4444444444..5555555555...
result: 15
..0000000000...1111111111...2222222222.....3333333333...4444444444...5555555555....
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(3)` results in

```
Active
000000000011111111112222222222333333333344444444445555555555
result: 15
...01010021212012010201121020.1.324422.344353434455354354343355555
result: 15
...0101100101110001010..1..2222222222....3444344333444334333..4..5555555555..
result: 15
...0000000000...1111111111...2222222222.....3333333333...4444444444...5555555555...
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(4)` results in

```
000000000011111111112222222222333333333344444444445555555555
result: 15
....21111210330203012133203010203211.324042342.4403454454545555555
result: 15
....0111001010100111001..220..22222222....344344343434334434..5353..55555555.
result: 15
....1010010011010000...121211...22222222.....4434334344344344...533533...55555555..
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(5)` results in

```
Active
000000000011111111112222222222333333333344444444445555555555
result: 15
.....1240130023102403134120412314320341042040.23122453134555555555
result: 15
.....102122112012021021220102..011...303030...3353453543553453..544555..44444
result: 15
.....10111110100101001...20020...22222222.....433443443443343344...353...555555555.
result: 15
```

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

The `map2` code can now be defined in terms of `Actor` as follows  (thanks, Francis!)

```
  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    es => {
      var optionalMa: Option[M[A]] = None
      var optionalMb: Option[M[B]] = None
      val atomicC: Atomic[M[C]] = new Atomic[M[C]]
      val combinerActor = new Actor[Either[M[A], M[B]]](es)({
        case Left(ma) =>
          if (optionalMb.isDefined) atomicC.setValue(toM(ab2c(ma.get, optionalMb.get.get)))
          else optionalMa = Some(ma)
        case Right(mb) =>
          if (optionalMa.isDefined) atomicC.setValue(toM(ab2c(optionalMa.get.get, mb.get)))
          else optionalMb = Some(mb)
      })
      combinerActor ! Left(pa(es))
      combinerActor ! Right(pb(es))

      atomicC.getValue
    }
```

The `combinerActor`'s `messageProcessor` processes 
*either an* `ma` *or an* `mb` and when both are received, 
it combines `ma` *and* `mb` to somehow produce an `mc`, effectively 
consuming a *sum* 
and 
producing a value depending on a *product*
 
evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(100)` results in

```
Active
...............000000000011111111112222222222333333333344444444445555555555
result: 15
...................0.....53402135400235013425104312543305321114030551240532154421422
result: 15
.....................................232500210424310005110532440214354312013450241351352314542355
result: 15
........................................320124130545233214043512404351524103405121354202103425310505
result: 15
```

evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(1)` results in

```
Active
..............000000000011111111112222222222333333333344444444445555555555
result: 15
.0000000000..1111111111..2222222222.....3333333333...4444444444..5555555555.....
result: 15
.0000000000...1111111111...2222222222........3333333333...4444444444...5555555555..........
result: 15
.0000000000....1111111111....2222222222.........3333333333....4444444444.....5555555555..........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(2)` results in

```
Active
................000000000011111111112222222222333333333344444444445555555555
result: 15
...1100011010001101011..0.....22333223323222322..3343..444545454454554......5555
result: 15
..0000000000...1111111111...2222222222........3333333333...4444444444...5555555555.........
result: 15
..0000000000....1111111111....2222222222.........3333333333....4444444444....5555555555..........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(3)` results in

```
Active
..................000000000011111111112222222222333333333344444444445555555555
result: 15
.....0122002200120122100102120......2..14331414431..54334345534435353.....545555
result: 15
....01011101110101001...20200....22222222.......4343434334344343434...3.....5555555555.....
result: 15
...0000000000....1111111111....2222222222.........3333333333....4444444444.....5555555555........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(4)` results in

```
Active
................000000000011111111112222222222333333333344444444445555555555
result: 15
...........2310133220202132311010320310233123..02..15400......5145455454454445455
result: 15
.....0110100011001101101...0.....2222222222......4344334344333434434...3......5555555555...
result: 15
.....0110110111001101....0222000.....2222222........4343344343433434343.4.........5555555555.....
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(5)` results in

```
Active
................000000000011111111112222222222333333333344444444445555555555
result: 15
.............1031204043213024043120243143400243102410...3234.....3152515552155555
result: 15
.......01202101121021001120102021....0....323232......43544355435445335434453.....4.....555
result: 15
......110011010101100101....020......222222222.......43334343444344344....3535553.......555555...
result: 15
```

Note that the behavior is not really fundamentally different (we're still kind of blocking in `atomicC.getValue`)


#### Reactive

The best choice for `M[A]` is probably `(A => Unit) => Unit`

Think of `A => Unit` as a *callback* and
think of `(A => Unit) => Unit` as a *callback invoker*
invoking callbacks *asynchronously*

```
object Reactive extends Common {

  type C[A] = A => Unit

  override type M[A] = C[A] => Unit

  override def fromM[A](ma: M[A]): A = {
    val atomic = new Atomic[A]
    val ca: A => Unit = a => atomic.setValue(a)
    ma(ca)
    atomic.getValue
  }

  override def toM[A](a: => A): M[A] =
  ca =>
    ca(a)

  private def toPar[A](ma: M[A]): Par[A] =
    es =>
      ca =>
        async(es)(ma(ca))

  private def toPar[A](a: => A): Par[A] =
    toPar(toM(a))

  override def map[A, B](pa: Par[A])(a2b: A => B): Par[B] =
    es =>
      cb => {
        val ma: M[A] = pa(es)
        val ca: A => Unit =
          a => {
            val pb: Par[B] = toPar(a2b(a))
            val mb: M[B] = pb(es)
            mb(cb)
          }
        ma(ca)
      }

  override def map2[A, B, C](pa: Par[A], pb: Par[B])(ab2c: (A, B) => C): Par[C] =
    es =>
      cC => {
        var optionalA: Option[A] = None
        var optionalB: Option[B] = None
        val combinerActor = new Actor[Either[A, B]](es)({
          case Left(a) =>
            if (optionalB.isDefined) {
              val pC: Par[C] = toPar(ab2c(a, optionalB.get))
              val mc: M[C] = pC(es)
              mc(cC)
            }
            else
              optionalA = Some(a)
          case Right(b) =>
            if (optionalA.isDefined) {
              val pC: Par[C] = toPar(ab2c(optionalA.get, b))
              val mc: M[C] = pC(es)
              mc(cC)
            }
            else
              optionalB = Some(b)
        })
        val ma: M[A] = pa(es)
        val ca: A => Unit = a => combinerActor ! Left(a)
        val mb: M[B] = pb(es)
        val cb: B => Unit = b => combinerActor ! Right(b)
        ma(ca)
        mb(cb)
      }

  override def flatMap[A, B](pa: Par[A])(a2pb: A => Par[B]): Par[B] =
    es =>
      cb => {
        val ma: M[A] = pa(es)
        val ca: A => Unit =
          a => {
            val pb: Par[B] = a2pb(a)
            val mb: M[B] = pb(es)
            mb(cb)
          }
        ma(ca)
      }

  override def fork[A](pa: => Par[A]): Par[A] =
    es =>
      ca => {
        val ma = pa(es)
        val p_a: Par[A] = toPar(ma)
        val m_a: M[A] = p_a(es)
        m_a(ca)
      }

}
```

Note that `toM` is rather simple while `fromM` is more complex


consider the following block of code (only the `import` (and, agreed, also the `println`) is different)

```
  {
    import Reactive._

    println("\nReactive")

    def verboseUnit[A](a: => A): Par[A] = unit(verbose(a))
    def verboseForkedUnit[A](a: => A): Par[A] = forkedUnit(verbose(a))

    println(s"\nresult: ${run(es)(map(sequence((0 to 5).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(map(sequence((0 to 5).toList.map(verboseForkedUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 5).toList.map(verboseUnit(_))))(_.sum))}")
    println(s"\nresult: ${run(es)(forkedMap(forkedSequence((0 to 5).toList.map(verboseForkedUnit(_))))(_.sum))}")

  }
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(100)` results in

```
Reactive
0000000000...1111111111...2222222222.........3333333333...4444444444.5..555555555...........
result: 15
......3250411513351123304554351223150034115024531...4025...403.2..440020...4.........22...........
result: 15
.................03411542403504211305342131052413542042105434...12501...032550...233...5.........2...........
result: 15
.......................4521325413032551435253041002101545023544145...32014104......200...31...22.........33........
result: 15
```

evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(1)` results in

```
Reactive
0000000000...1111111111...2222222222.........3333333333...4444444444...5555555555...........
result: 15
.0000000000.1111111111.2222222222.3333333333.4444444444.5555555555..........................
result: 15
........0000000000...3333333333....1111111111.2222222222..4444444444.5555555555........................
result: 15
..............0000000000...3333333333....1111111111.2222222222..4444444444.5555555555........................
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(2)` results in

```
Reactive
0000000000...1111111111...2222222222.........3333333333...4444444444...5555555555...........
result: 15
..1011101100101011.020200.22323333222322.34443433.45454545455554..................55...........
result: 15
...........3003303003300303303...0.4545545545444554545..4.21222212122212..................111111...........
result: 15
.................03303303030303033..10010..1122212112221112..22..4454545454545454554.................5...........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(3)` results in

```
Reactive
0000000000...1111111111...2222222222.........3333333333...4444444444...5555555555...........
result: 15
...021201222100122011102102012.01.30.433454345344553534335453...............454...55...........
result: 15
..............300313101300133103103031103.0.12241.5425245245255425242452................545...44...........
result: 15
....................30310031301330110303313.00210.124121.422554425425254242................54544...555...........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(4)` results in

```
Reactive
0000000000...1111111111...2222222222..3.......333333333...4444444444...5555555555...........
result: 15
....231012310203132033201203031230112213.401.20...............55445544545554455...44...........
result: 15
...............113200212132301301021230102321021.350352.530............453...4545454454545...44...........
result: 15
....................23310233103222101103122302011033121..02..3.........400......54555455454454545...44...........
result: 15
```


evaluating the block of code above with `val es: ExecutorService = Executors.newFixedThreadPool(5)` results in

```
Reactive
0000000000...1111111111...2222222222..3.......333333333...4444444444...5555555555...........
result: 15
.....1324433041020340142034301032304102444.31520......53...5152125121...552.........555...........
result: 15
................32442304132410120314021432010342013204120312.1.......54530......4.5..3...5555555...........
result: 15
.....................0543055130143550131451301141510531...42330502345.....2020...3...44244.........22222...........
result: 15
```

Processing resources are used mor efficiently now.




