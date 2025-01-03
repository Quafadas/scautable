---
title: Strongly Typed CSVs
date: 2024-01-02
author: Simon Parten
---

## Motivation

I have found myself often disatisfied with the experience of working with CSV files in scala. Which is a frustrating statement. It _feels_ like something that scala should excel at.

As I tried to analyse _why_, I concluded that scala was great, _if the data model was already known_. However, when one recieved an essentially random CSV, creating the data model is kind of painful. I felt that it took too long to deal with that part - parsing the data a than I was actually understanding the data. In particular, I was forced to commit to a strongly typed data model, before I understood the data itself.

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






