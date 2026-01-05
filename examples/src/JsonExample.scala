package examples

import io.github.quafadas.scautable.json.*
import io.github.quafadas.table.TypeInferrer

object JsonExample:
  def main(args: Array[String]): Unit =
    // Example 1: Simple JSON parsing
    inline val simpleJson = """[
      {"name": "Alice", "age": 30, "score": 95.5},
      {"name": "Bob", "age": 25, "score": 87.0},
      {"name": "Charlie", "age": 35, "score": 92.3}
    ]"""
    
    val result1 = JSON.fromString(simpleJson)
    println("Example 1: Simple JSON parsing")
    println("================================")
    result1.foreach { row =>
      println(s"${row.name} (age ${row.age}) scored ${row.score}")
    }
    println()

    // Example 2: Handling missing fields
    inline val missingFieldsJson = """[
      {"id": 1, "name": "Product A", "price": 10.99},
      {"id": 2, "name": "Product B"},
      {"id": 3, "price": 15.50}
    ]"""
    
    val result2 = JSON.fromString(missingFieldsJson)
    println("Example 2: Handling missing fields")
    println("===================================")
    result2.foreach { row =>
      val name = row.name.getOrElse("N/A")
      val price = row.price.map(p => f"$$$p%.2f").getOrElse("N/A")
      println(s"Product ${row.id.getOrElse("?")} : $name - $price")
    }
    println()

    // Example 3: Type inference strategies
    inline val mixedJson = """[
      {"a": 1, "b": "hello"},
      {"a": 2, "b": "world"}
    ]"""
    
    println("Example 3: Type inference strategies")
    println("====================================")
    
    // Default: FromAllRows
    val result3a = JSON.fromString(mixedJson)
    println("With FromAllRows (default):")
    result3a.foreach { row =>
      println(s"  a=${row.a} (${row.a.getClass.getSimpleName}), b=${row.b} (${row.b.getClass.getSimpleName})")
    }
    
    // StringType: All fields as String
    val result3b = JSON.fromString(mixedJson, TypeInferrer.StringType)
    println("With StringType:")
    result3b.foreach { row =>
      println(s"  a=${row.a} (${row.a.getClass.getSimpleName}), b=${row.b} (${row.b.getClass.getSimpleName})")
    }
    println()

    // Example 4: Boolean and null handling
    inline val booleanJson = """[
      {"active": true, "count": 10},
      {"active": false, "count": null},
      {"active": null, "count": 5}
    ]"""
    
    val result4 = JSON.fromString(booleanJson)
    println("Example 4: Boolean and null handling")
    println("====================================")
    result4.foreach { row =>
      val active = row.active.map(_.toString).getOrElse("null")
      val count = row.count.map(_.toString).getOrElse("null")
      println(s"  active=$active, count=$count")
    }

end JsonExample
