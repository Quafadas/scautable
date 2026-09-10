# Workbooks (Almond)

`CSV.relativeToSource` and `CSV.projectRoot` have to find your data twice - once at compile time, so the compiler can type the columns, and again at runtime. In a normal project both halves are easy: the macro anchors to the source file that called it.

Notebooks break that assumption, in two different ways at once.

## Why a notebook is different

A notebook cell has **no source file on disk**. Almond compiles cells from memory in the kernel JVM, so there is nothing to anchor to and scautable falls back to the compiler's working directory.

Worse, if you are also driving the notebook through a tool that provides LSP support by writing each cell out to a scala-cli script, that script gets compiled by a **build server daemon**. Macros expand inside the compiler, and that compiler lives in a long running process whose working directory is a cache directory:

```
scautable: could not read a CSV at '/Users/you/Library/Caches/ScalaCli/bloop/titanic.csv' while compiling.
```

That path is the Bloop daemon's own cache. It has no relationship to your notebook, or to any of your code.

So the two front ends disagree about the source file *and* about the working directory. Neither can derive where your notebook actually lives, because that is host context, not compilation context.

## Declare the anchor

The fix is to stop guessing and tell scautable outright. A declared anchor takes priority over everything scautable would otherwise infer, including an on disk call site - which is the point, since the scratch file a LSP sidecar compiles is on disk, just in the wrong place.

There are two channels, because the two hosts cannot both reach the same one.

### In Almond: a system property

Almond compiles cells in its own JVM, so a property set in one cell is visible to macro expansion in every later cell. Point it at the directory your data actually lives in, in a cell of its own:

```scala
System.setProperty("scautable.root", "/path/to/your/data")
```

Then, in any later cell:

```scala
import io.github.quafadas.table.*
val titanic = CSV.relativeToSource("titanic.csv")
```

It has to be a *separate, earlier* cell. A macro expands when its own cell is compiled, so a `setProperty` in the same cell runs far too late - the cell fails exactly as if you had never set it.

This also sidesteps the classpath problem - you do not need to get the data onto Almond's classpath, you just need to tell the macro where to look.

**If your data sits next to the notebook, you may not need this at all.** Jupyter starts kernels with their working directory set to the notebook's own directory, so the working directory fallback usually finds it, and the only cost is the compile time warning. The property earns its keep when the data is somewhere else, or when a LSP sidecar is compiling your cells from a scratch file elsewhere on disk.

That same fact is a trap when testing this: put the CSV next to the notebook and the call succeeds whether or not your anchor was picked up. To convince yourself the anchor is doing the work, keep the data somewhere that is *not* the kernel's working directory.

### Behind a build server: `-Xmacro-settings`

A `-D` on your build never reaches a Bloop or BSP daemon, because the daemon is a different process that was started long beforehand. `-Xmacro-settings` travels with the compile request itself, so it does:

```sh
scala-cli run . -O -Xmacro-settings:scautable.root=/path/to/notebook/dir
```

or as a using directive:

```scala
//> using option -Xmacro-settings:scautable.root=/path/to/notebook/dir
```

If you are building the LSP sidecar, this is the flag to emit alongside each generated script. Point it at the notebook's directory and both halves agree.

## Diagnosing it

When resolution falls through to the working directory, scautable warns and says so. A compile time miss names the channel that chose the directory it searched:

```
That path was anchored to the compiler's working directory.
```

versus

```
That path was anchored to the 'scautable.root' compiler setting.
```

which tells you whether your anchor was picked up at all.

## The escape hatches

`CSV.absolutePath` and `CSV.resource` do not anchor, so they are unaffected by any of the above. `absolutePath` needs a compile time constant, but string concatenation of constants folds, so an `inline val` works:

```scala
inline val root = "/Users/you/notebooks"
val titanic = CSV.absolutePath(root + "/titanic.csv")
```

This is worth knowing, but it is a stopgap rather than a fix - it bakes a machine specific absolute path into the notebook, so it breaks as soon as anyone else opens it. Prefer a declared anchor.
