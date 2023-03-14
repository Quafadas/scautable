//> using lib "org.scalameta::munit:1.0.0-M7"
//> using lib "com.lihaoyi:::scalatags:0.12.0"

import scala.deriving.Mirror
import scala.compiletime.erasedValue
import scala.compiletime.constValue
import scala.compiletime.summonInline

package object scautable {

  // Aggressively copy-pasta-d from here; https://blog.philipp-martini.de/blog/magic-mirror-scala3/
  inline def getTypeclassInstances[A <: Tuple]: List[PrettyString[Any]] =
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil
      case _: (head *: tail) =>
        val headTypeClass =
          summonInline[PrettyString[
            head
          ]] // summon was known as implicitly in scala 2
        val tailTypeClasses =
          getTypeclassInstances[tail] // recursive call to resolve also the tail
        headTypeClass
          .asInstanceOf[PrettyString[Any]] :: getTypeclassInstances[tail]
    }

// helper method like before
  inline def summonInstancesHelper[A](using m: Mirror.Of[A]) =
    getTypeclassInstances[m.MirroredElemTypes]

  // this traits can just be copy/pasted or reside in a library
  trait EasyDerive[TC[_]] {
    final def apply[A](using tc: TC[A]): TC[A] = tc

    case class CaseClassElement[A, B](
      label: String,
      typeclass: TC[B],
      getValue: A => B,
      idx: Int
    )
    case class CaseClassType[A](
      label: String,
      elements: List[CaseClassElement[A, _]],
      fromElements: List[Any] => A
    )

    case class SealedElement[A, B](
      label: String,
      typeclass: TC[B],
      idx: Int,
      cast: A => B
    )
    case class SealedType[A](
      label: String,
      elements: List[SealedElement[A, _]],
      getElement: A => SealedElement[A, _]
    )

    inline def getInstances[A <: Tuple]: List[TC[Any]] =
      inline erasedValue[A] match {
        case _: EmptyTuple => Nil
        case _: (t *: ts) =>
          summonInline[TC[t]].asInstanceOf[TC[Any]] :: getInstances[ts]
      }

    inline def getElemLabels[A <: Tuple]: List[String] =
      inline erasedValue[A] match {
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => constValue[t].toString :: getElemLabels[ts]
      }

    def deriveCaseClass[A](caseClassType: CaseClassType[A]): TC[A]

    def deriveSealed[A](sealedType: SealedType[A]): TC[A]

    inline given derived[A](using m: Mirror.Of[A]): TC[A] = {
      val label         = constValue[m.MirroredLabel]
      val elemInstances = getInstances[m.MirroredElemTypes]
      val elemLabels    = getElemLabels[m.MirroredElemLabels]

      inline m match {
        case s: Mirror.SumOf[A] =>
          val elements = elemInstances.zip(elemLabels).zipWithIndex.map { case ((inst, lbl), idx) =>
            SealedElement[A, Any](
              lbl,
              inst.asInstanceOf[TC[Any]],
              idx,
              identity
            )
          }
          val getElement = (a: A) => elements(s.ordinal(a))
          deriveSealed(SealedType[A](label, elements, getElement))

        case p: Mirror.ProductOf[A] =>
          val caseClassElements =
            elemInstances
              .zip(elemLabels)
              .zipWithIndex
              .map { case ((inst, lbl), idx) =>
                CaseClassElement[A, Any](
                  lbl,
                  inst.asInstanceOf[TC[Any]],
                  (x: Any) => x.asInstanceOf[Product].productElement(idx),
                  idx
                )
              }
          val fromElements: List[Any] => A = { elements =>
            val product: Product = new Product {
              override def productArity: Int = caseClassElements.size

              override def productElement(n: Int): Any = elements(n)

              override def canEqual(that: Any): Boolean = false
            }
            p.fromProduct(product)
          }
          deriveCaseClass(
            CaseClassType[A](label, caseClassElements, fromElements)
          )
      }
    }
  }

  trait PrettyString[A] {
    def tableRow(a: A): ReactiveHtmlElement[TableRow]    = ???
    def tableCell(a: A): ReactiveHtmlElement[TableCell]  = ???
    def tableHeader(a: A): ReactiveHtmlElement[TableRow] = ???
  }

  object PrettyString extends EasyDerive[PrettyString] {
    override def deriveCaseClass[A](
      productType: CaseClassType[A]
    ): PrettyString[A] = new PrettyString[A] {
      override def tableHeader(a: A): ReactiveHtmlElement[TableRow] = ???
      override def tableCell(a: A): ReactiveHtmlElement[TableCell]  = ???
      override def tableRow(a: A): ReactiveHtmlElement[TableRow] = {
        // println("table row in pretty string")
        if (productType.elements.isEmpty) tr("empty")
        else {
          val prettyElements =
            productType.elements.map(p => p.typeclass.tableCell(p.getValue(a)))
          tr(
            prettyElements
          )
        }
      }
    }
    override def deriveSealed[A](sumType: SealedType[A]): PrettyString[A] =
      new PrettyString[A] {
        override def tableHeader(a: A): ReactiveHtmlElement[TableRow] = ???
        override def tableCell(a: A): ReactiveHtmlElement[TableCell]  = ???
        override def tableRow(a: A): ReactiveHtmlElement[TableRow]    = ???
      }
  }

  given stringPrettyString: PrettyString[String] = new PrettyString[String] {
    override def tableHeader(a: String): ReactiveHtmlElement[TableRow] = ???
    override def tableCell(a: String): ReactiveHtmlElement[TableCell]  = td(a)
    override def tableRow(a: String): ReactiveHtmlElement[TableRow]    = ???
  }

  given intPrettyString: PrettyString[Int] = new PrettyString[Int] {

    override def tableHeader(a: Int): ReactiveHtmlElement[TableRow] = ???

    override def tableCell(a: Int): ReactiveHtmlElement[TableCell] = td(a)

    override def tableRow(a: Int): ReactiveHtmlElement[TableRow] = ???
  }

  given longPrettyString: PrettyString[Long] = new PrettyString[Long] {
    override def tableHeader(a: Long): ReactiveHtmlElement[TableRow] = ???
    override def tableCell(a: Long): ReactiveHtmlElement[TableCell]  = td(s"$a")
    override def tableRow(a: Long): ReactiveHtmlElement[TableRow]    = ???
  }

  given datePrettyString: PrettyString[LocalDateJ] =
    new PrettyString[LocalDateJ] {
      override def tableHeader(a: LocalDateJ): ReactiveHtmlElement[TableRow] =
        ???
      override def tableCell(a: LocalDateJ): ReactiveHtmlElement[TableCell] =
        td(a.value.toString())
      override def tableRow(a: LocalDateJ): ReactiveHtmlElement[TableRow] = ???
    }

  given doublePrettyString: PrettyString[Double] = new PrettyString[Double] {
    override def tableHeader(a: Double): ReactiveHtmlElement[TableRow] = ???
    override def tableCell(a: Double): ReactiveHtmlElement[TableCell] = td(
      s"$a"
    )
    override def tableRow(a: Double): ReactiveHtmlElement[TableRow] = ???
  }

  given booleanPrettyString: PrettyString[Boolean] = new PrettyString[Boolean] {
    override def tableHeader(a: Boolean): ReactiveHtmlElement[TableRow] = ???
    override def tableCell(a: Boolean): ReactiveHtmlElement[TableCell] = td(
      s"$a"
    )
    override def tableRow(a: Boolean): ReactiveHtmlElement[TableRow] = ???
  }

  def deriveTableRow[A](a: A)(using prettyStringInstance: PrettyString[A]) =
    prettyStringInstance.tableRow(a)

  def deriveTableHeader[A](a: A)(using prettyStringInstance: PrettyString[A]) =
    println("deriveTableHeader")
    tr(prettyStringInstance.tableRow(a))

  inline def getElemLabels[A <: Tuple]: List[String] =
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil // stop condition - the tuple is empty
      case _: (head *: tail) => // yes, in scala 3 we can match on tuples head and tail to deconstruct them step by step
        val headElementLabel =
          constValue[head].toString // bring the head label to value space
        val tailElementLabels =
          getElemLabels[tail]                 // recursive call to get the labels from the tail
        headElementLabel :: tailElementLabels // concat head + tail
    }

  inline def tableHeader[A](using m: Mirror.ProductOf[A]) =
    val elemLabels = getElemLabels[m.MirroredElemLabels]
    tr(elemLabels.map(th(_)))

  inline def derivePrettyStringCaseClass[A](using m: Mirror.ProductOf[A]) =
    new PrettyString[A] {

      override def tableHeader(a: A): ReactiveHtmlElement[TableRow] =
        val elemLabels = getElemLabels[m.MirroredElemLabels]
        tr(elemLabels.map(th(_)))

      override def tableCell(a: A): ReactiveHtmlElement[TableCell] = ???

      override def tableRow(a: A): ReactiveHtmlElement[TableRow] = {
        val elemLabels    = getElemLabels[m.MirroredElemLabels]
        val elemInstances = getTypeclassInstances[m.MirroredElemTypes]
        val elems =
          a.asInstanceOf[Product].productIterator // every case class implements scala.Product, we can safely cast here
        val elemCells = elems
          .zip(elemInstances)
          .map { (elem, instance) =>
            instance.tableCell(elem)
          }
          .toList
        tr(
          elemCells
        )
      }
    }

  def apply() = "hi"
}

@main def runSomething = println(scautable())
