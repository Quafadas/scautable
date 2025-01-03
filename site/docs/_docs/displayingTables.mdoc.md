---
title: Displaying Tables
---


```scala

//> using dep io.github.quafadas::scautable:{{projectVersion}}

```

# Elevator Pitch

```scala
import io.github.quafadas.table.*

case class ScauTest(anInt: Int, aString: String)

val table = Seq(ScauTest(1, "one"), ScauTest(2, "two"), ScauTest(3, "booyakashaha!"))

println(table.consoleFormat(fancy = false))

```

On the JVM in particular, the ability to pop it open in the browser, see and search the actual data... can be useful. Particularly if you're working with a lot of messy, csv data for example.

```scala
import io.github.quafadas.table.*

case class ScauTest(anInt: Int, aString: String)
val soComplex = Seq(ScauTest(1, "one"), ScauTest(2, "two"))

scautable.desktopShow(soComplex)
```
Will pop open a browser... using https://datatables.net
![desktop](../_assets/desktop.png)

And your case classes are now easily visible and searchable.

```scala


```

# Scala JS

I love scala JS, so it cross compiles, and gives you back a scalatags table. This is of questionable usefulness, but it's fun.

TODO : Laminar integration, which nobody wants but would be cool.