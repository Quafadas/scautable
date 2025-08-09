# Scala 3 Macro Pattern Matching Issue with Exports

## Summary

When using quoted pattern matching (`'{...}'`) in Scala 3 macros, patterns fail to match expressions that reference types through `export` statements, even when the exported type is structurally identical to the original. This forces macro authors to implement complex tree-based pattern matching as a workaround.

## The Problem

### Scenario
1. A macro uses quoted pattern matching to handle different enum cases: `'{ MyEnum.Case[T]() }'`
2. Users import the enum through a package object that re-exports it: `export some.package.MyEnum`  
3. The macro pattern fails to match, even though semantically it should work

### Root Cause
The compiler represents exported references with their full import path in the expression tree:
- Direct usage: `some.package.MyEnum.Case[T]()`
- Via export: `exported.package.MyEnum.Case[T]()` (internally becomes the export path)

Quoted patterns match structurally on the exact paths, so `'{ MyEnum.Case[T]() }'` won't match an expression that internally references `exported.package.MyEnum.Case[T]()`.

## Minimal Reproduction

```scala
// MyEnum.scala
package original

enum MyEnum:
  case StringCase
  case TypedCase[T]()

object MyEnum:
  inline def create[T]: MyEnum = TypedCase[T]()
```

```scala  
// Exports.scala
package exported

object api:
  export original.MyEnum
  export original.MyEnum.*
```

```scala
// Macro.scala  
package macro

import scala.quoted.*

object MacroImpl:
  def handleEnum(expr: Expr[original.MyEnum])(using Quotes): Expr[String] =
    import quotes.reflect.*
    
    expr match
      case '{ original.MyEnum.TypedCase[t]() } =>
        '{ "Matched direct path" }
      case '{ MyEnum.TypedCase[t]() } =>  
        '{ "Matched local import" }
      case _ =>
        // This is what we're forced to do as a workaround
        val tree = expr.asTerm
        // ... complex tree traversal logic
        '{ "Fallback tree match" }

  inline def testMacro(inline e: original.MyEnum): String = 
    ${ handleEnum('e) }
```

```scala
// Usage.scala
import exported.api.*

@main def test(): Unit =
  // This works - direct path
  val direct = MacroImpl.testMacro(original.MyEnum.TypedCase[Int]())
  
  // This fails pattern matching and hits fallback - uses export path
  val viaExport = MacroImpl.testMacro(MyEnum.TypedCase[Int]())
```

## Current Workaround

Macro authors must implement tree-based pattern matching:

```scala
def handleEnum(expr: Expr[MyEnum])(using Quotes): Expr[String] =
  import quotes.reflect.*
  
  expr match
    case '{ MyEnum.TypedCase[t]() } =>
      '{ "Direct match worked" }
    case _ =>
      // Fallback: manual tree traversal
      val tree = expr.asTerm
      
      def unwrapInlined(term: Term): Term = term match
        case Inlined(_, _, body) => unwrapInlined(body)
        case Typed(expr, _) => unwrapInlined(expr)  
        case other => other
      
      unwrapInlined(tree) match
        case Apply(TypeApply(Select(Select(_, "TypedCase"), "apply"), List(tpe)), Nil) =>
          tpe.tpe.asType match
            case '[t] => '{ "Tree match worked" }
            case _ => '{ "Failed to extract type" }
        case _ =>
          '{ "No pattern matched" }
```

## Potential Compiler Improvement

The compiler could normalize exported references during pattern matching, allowing:

```scala
expr match
  case '{ MyEnum.TypedCase[t]() } =>
    // Should match regardless of whether MyEnum was imported directly 
    // or through an export statement
```

This would make macros more robust and eliminate the need for complex workaround code.

## Impact

This affects any macro that:
- Uses quoted pattern matching on types that might be imported via exports
- Needs to work with both direct imports and re-exported APIs
- Currently requires maintaining multiple pattern cases or complex fallback logic

Libraries providing both direct APIs and convenience re-exports are particularly affected, as they must choose between user-friendly exports and macro compatibility.

## Real-World Context

This issue was encountered in the `scautable` library when implementing `TypeInferrer.FromTuple[T]()` pattern matching. The library provides a `table` package object that re-exports the main API for convenience, but this broke macro pattern matching for users who imported via `table.*`.

The fix required implementing tree-based pattern matching with `Inlined` node unwrapping, significantly complicating the macro code that should have been a simple quoted pattern match.