import scalatags.Text.all.*
import scala.deriving.Mirror
import scala.compiletime.erasedValue
import scala.compiletime.constValue
import scala.compiletime.summonInline
import java.time.LocalDate

package object scautable {

  // Aggressively copy-pasta-d from here; https://blog.philipp-martini.de/blog/magic-mirror-scala3/
  inline def getTypeclassInstances[A <: Tuple]: List[HtmlTableRender[Any]] =
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil
      case _: (head *: tail) =>
        val headTypeClass =
          summonInline[HtmlTableRender[
            head
          ]] // summon was known as implicitly in scala 2
        val tailTypeClasses =
          getTypeclassInstances[tail] // recursive call to resolve also the tail
        headTypeClass
          .asInstanceOf[HtmlTableRender[Any]] :: getTypeclassInstances[tail]
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

  trait HtmlTableRender[A] {
    def tableRow(a: A): scalatags.Text.TypedTag[String]    = ???
    def tableCell(a: A): scalatags.Text.TypedTag[String]   = ???
    def tableHeader(a: A): scalatags.Text.TypedTag[String] = ???
  }

  object HtmlTableRender extends EasyDerive[HtmlTableRender] {
    override def deriveCaseClass[A](
      productType: CaseClassType[A]
    ): HtmlTableRender[A ] = new HtmlTableRender[A] {
      override def tableHeader(a: A) = ???
      override def tableCell(a: A) = 
        // val b = a.asInstanceOf[Product]
        // val h    = b.productElementNames.toList
        // val header = tr(h.map(th(_)))
        // val rows = tableDeriveInstance.tableRow(a)
        // table(tbody(header,rows))
        // scautable(a)
        throw new Exception("compound case classes not foreseen")        
      override def tableRow(a: A): scalatags.Text.TypedTag[String] = {
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
    override def deriveSealed[A](sumType: SealedType[A]): HtmlTableRender[A] =
      new HtmlTableRender[A] {
      }
  }

  given stringT: HtmlTableRender[String] = new HtmlTableRender[String] {
    override def tableCell(a: String)   = td(a)
  }

  given intT: HtmlTableRender[Int] = new HtmlTableRender[Int] {
    override def tableCell(a: Int) = td(a)    
  }

  given longT: HtmlTableRender[Long] = new HtmlTableRender[Long] {
    override def tableCell(a: Long)   = td(s"$a")
  }

  given doubleT: HtmlTableRender[Double] = new HtmlTableRender[Double] {
    override def tableCell(a: Double) = td(
      s"$a"
    )
  }

  given booleanT: HtmlTableRender[Boolean] = new HtmlTableRender[Boolean] {
    override def tableCell(a: Boolean) = td(
      s"$a"
    )
  }

  def deriveTableRow[A](a: A)(using instance: HtmlTableRender[A]) =
    instance.tableRow(a)

  def deriveTableHeader[A](a: A)(using instance: HtmlTableRender[A]) =
    println("deriveTableHeader")
    tr(instance.tableRow(a))

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

  inline def tableHeader[A](using m: Mirror.Of[A]) =
    val elemLabels = getElemLabels[m.MirroredElemLabels]
    tr(elemLabels.map(th(_)))

  inline def deriveCaseClass[A](using m: Mirror.ProductOf[A]) =
    new HtmlTableRender[A] {

      override def tableHeader(a: A) =
        val elemLabels = getElemLabels[m.MirroredElemLabels]
        tr(elemLabels.map(th(_)))

      override def tableCell(a: A) = ???

      override def tableRow(a: A) = {
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

  def apply[A <: Product](a: Seq[A])(using tableDeriveInstance: HtmlTableRender[A]) =    
    val h    = a.head.productElementNames.toList
    val header = tr(h.map(th(_)))
    val rows = for(r <- a) yield {tableDeriveInstance.tableRow(r)}
    table(tbody(header,rows))
}
