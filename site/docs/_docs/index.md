Auto Magically generate and view searchable html tables for a `Seq[A]`


```scala

//> using dep io.github.quafadas::scautable:{{projectVersion}}

```

```scala
import io.github.quafadas.scautable.{*, given}

case class ScauTest(anInt: Int, aString: String)
scautable.desktopShow(
  Seq(
    ScauTest(1, "one"), ScauTest(2, "two")
  )
)
```

