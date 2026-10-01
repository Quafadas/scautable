package io.github.quafadas.scautable

/** A `CsvIterator` owns the handle it reads from.
  *
  * Before it did, `scala.io.Source` was opened, its line iterator kept, and the `Source` itself dropped on the floor - so nothing ever closed it. The handle came back only when
  * the JVM noticed the object was unreachable, which is tied to garbage collection rather than to reading, and 20k reads of a small file could stack up thousands of descriptors
  * before any were returned.
  *
  * These use a counting stand-in for the source, so the assertions are about the iterator's contract rather than about file descriptors, and hold on every platform.
  */
class IteratorResourceSuite extends munit.FunSuite:

  class Spy extends AutoCloseable:
    var closes = 0
    override def close(): Unit = closes += 1
  end Spy

  private def iteratorOf(spy: Spy, rows: String*) =
    new CsvIterator[("a", "b"), (String, String)](rows.iterator, Seq("a", "b"), ',', Some(spy))

  test("draining the iterator releases the source") {
    val spy = new Spy
    val itr = iteratorOf(spy, "1,2", "3,4")
    assertEquals(spy.closes, 0, "opening must not close anything")
    assertEquals(itr.toList.map(r => (r.a, r.b)), List(("1", "2"), ("3", "4")))
    assertEquals(spy.closes, 1, "running out of rows should close the source")
  }

  test("an empty file is closed by the first hasNext") {
    val spy = new Spy
    val itr = iteratorOf(spy)
    assert(!itr.hasNext)
    assertEquals(spy.closes, 1)
  }

  test("close is idempotent, and closing mid-read ends the iteration") {
    val spy = new Spy
    val itr = iteratorOf(spy, "1,2", "3,4", "5,6")
    assertEquals(itr.next().a, "1")
    itr.close()
    assertEquals(spy.closes, 1)
    assert(!itr.hasNext, "a closed iterator has no more rows")
    intercept[NoSuchElementException](itr.next())

    // exhausting an already closed iterator must not close it twice
    itr.close()
    assertEquals(spy.closes, 1)
  }

  test("closing after a full drain does not close twice") {
    val spy = new Spy
    val itr = iteratorOf(spy, "1,2")
    itr.toList
    itr.close()
    assertEquals(spy.closes, 1)
  }

  test("it is an AutoCloseable, so Using releases it on an early exit") {
    val spy = new Spy
    val first = scala.util.Using(iteratorOf(spy, "1,2", "3,4", "5,6"))(_.next().a)
    assertEquals(first, scala.util.Success("1"))
    assertEquals(spy.closes, 1, "Using should close even though only one row was read")
  }

  test("Using closes even when the body throws") {
    val spy = new Spy
    val boom = scala.util.Using(iteratorOf(spy, "1,2"))(_ => throw new RuntimeException("boom"))
    assert(boom.isFailure)
    assertEquals(spy.closes, 1)
  }

  test("an iterator with no source at all still behaves") {
    val itr = new CsvIterator[("a", "b"), (String, String)](Iterator("1,2"), Seq("a", "b"))
    assertEquals(itr.toList.map(_.a), List("1"))
    itr.close()
  }

end IteratorResourceSuite
