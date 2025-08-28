---
title: Strongly Typed, Zero Boilerplate CSVs
date: 2024-01-02
author: Simon Parten
---

## Motivation

I have found myself often disatisfied with the experience of working with CSV files in scala. Which is a frustrating statement. It _feels_ like something that scala should excel at.

As I tried to analyse _why_, I concluded that scala was great, _if the data model was already known_. However, when one recieved an essentially random CSV, creating the data model is kind of painful. I felt that it took too long to deal with that part - getting data to the point where I couild play with it was taking longer than actually understanding the data. In particular, I felt forced to commit to a strongly typed data model, before I understood the data itself.

This library seeks to alter that experience.

## The Idea

Instead of writing the data model upfront, we're going to do something that is (arguably) a bit crazy. We're going to write a macro which reads the first line of the file and injects the column headers into the type system. In other words, for a CSV file which looks like this;

```csv
col1, col2, col3
1, 2, 3
4, 5, 6
```

We're going to write a macro which satisfies this code;

```scala sc:nocompile
def csv : CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
```
CSV iterator extends iterator;

```scala sc:nocompile
 class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K, Tuple[String]]]
```
The above is "type pseudo code", it wouldn't compile, but it conveys the idea. Our actual code is likely... messier.

## Implementation

Surprisingly, it works, bringing our CSV file inside the compile scope.

```scala sc:nocompile
transparent inline def resource[T](inline path: String) = ${ readCsvResource('path) }

private def readCsvResource(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if (resourcePath == null) {
      report.throwError(s"Resource not found: $path")
    }
    val source = Source.fromResource(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](resourcePath.getPath.toString())
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

```


## Usage

Here's a [scastie](https://scastie.scala-lang.org/Quafadas/2JoRN3v8SHK63uTYGtKdlw/26) to a scastie which does some manipulation on the Titanic dataset.

It showcases the basic idea, that we can manipulation the named tuples during the iteration and data gathering stage, and then wheel in the stdlib to do much of the heavy lifting.

