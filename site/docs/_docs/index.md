Auto Magically generate and view searchablehtml tables a `Seq[A]`


```scala

//> using dep io.github.quafadas::scautable:{{projectVersion}}

```

```scala
import io.github.quafadas.scautable.{*, given}

case class ScauTest(anInt: Int, aString: String)
scautable(
  Seq(ScauTest(1, "one"), ScauTest(2, "two")),
  true
).toString()
```