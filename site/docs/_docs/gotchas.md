

# Large Csv Files

For files with many columns, the strategy of injecting the column names into the compiler is somwehat risky, and as you get larger number of columns (cca 1000), you may begin to experience compiler crashes. 

A 5000 column CSV with the following JVM settings 

```sh
-Xss100m
-Xmx10G
```

and args to the scala compiler 

```scala sc:nocompile
def scalacOptions: T[Seq[String]] = Seq("-Xmax-inlines:10000")
```
passed the below test. 
```scala sc:nocompile
  test("5000 cols") {
    def csv = CSV.resource("testFile/5000Cols.csv")    
    assert(csv.headers.length == 5000)      
  }
```

Please bear in mind that scautable is targeted in the small, and beyond this point may not be the right strategy for your data analysis.

The number of rows should not be problematic as the design it lazy in nature - although again please bear in mind that we are targeting "small". Scautable is untested beyond a couple of million rows 🤷.

# scala-cli 

My favoured approach is to use the `compileDir` [directive](https://scala-cli.virtuslab.org/docs/reference/directives/#resource-directories) to setup a resource folder as part of the project.




# Mill

Is my preferred build tool. Note that this;

```scala sc:nocompile
def csv : CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
```

will not work. Frustrating, given that `os.resource / "simple.csv" ` will. The answer is that mill seperates compile resources and run resources. From the compilers point of view, "simple.csv" is indeed. Not part of the resources.

Now that we know this, it's easy enough to work around. 

```scala sc:nocompile
trait ShareCompileResources extends ScalaModule {
  override def compileResources = super.compileResources() ++ resources()
}
```
And the lines above should work.
