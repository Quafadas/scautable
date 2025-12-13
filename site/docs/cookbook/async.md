# Async

One situation for me is enriching table data from an external source, that is perhaps a network call away.

Making all those calls in sequence can be slow...

But it we call the `addColumn` method on an already materialised collection... we can get async behaviour. The strategy below works, but is naive... useful for a quick and dirty

```scala mdoc

import io.github.quafadas.table.*
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

val data = CSV.fromString("id\n1\n2\n3").toSeq

def slowNetworkFetch(id: Int) = {
    blocking {
      Thread.sleep(1000)
      s"The Answer $id"
    }
}

println(
data
  .addColumn["answer", Future[String]](
    row =>
      Future(slowNetworkFetch(row.id))
  )
  .mapColumn["answer", String](Await.result(_, Duration.Inf))
  .consoleFormatNt(fansi = false)
)
```

If we want something more "idiomatic" in terms of `Future` handling, we can drop down into our knowledge of stdlib, traverse, map, zip and join...

```scala mdoc

val d2 = data
  .addColumn["answer_tmp", Future[String]](
    row =>
      Future(slowNetworkFetch(row.id))
  )

val resolved = Await.result(Future.traverse(d2.column["answer_tmp"])(identity), Duration.Inf)

println(
d2.zip(resolved).map{ (row, res) =>
  row ++ (answer = res)
}
.dropColumn["answer_tmp"]
.consoleFormatNt(fansi = false)
)
```
