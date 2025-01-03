---
Title: Strongly Typed CSVs
---

# Strongly Typed CSVs

## Motivation

I have found myself often disatisfied with the experience of working with CSV files in scala. Which is weird, because it should be something that scala excels at.

As I tried to analyse _why_, I concluded that scala was great, _if the data model was already known_ and written down. However, when one recieved an essentially random CSV, creating the data model is kind of painful. It was taking too long to deal with that part, and parsing the data, than I was actually undersstanding the data.

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
CSV iterator will look like this;


