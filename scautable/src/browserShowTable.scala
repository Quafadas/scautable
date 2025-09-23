package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.annotation.nowarn
import scala.compiletime.constValue
import scala.compiletime.constValueTuple
import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror
import scalatags.Text.TypedTag
import scalatags.Text.all.*

/** This is a simple library to render a scala case class as an html table. It assumes the presence of a [[HtmlTableRender]] instance for each type in the case class.
  */
object HtmlRenderer extends PlatformSpecific:

  // Aggressively copy-pasta-d from here; https://blog.philipp-martini.de/blog/magic-mirror-scala3/
  protected inline def getTypeclassInstances[A <: Tuple]: List[HtmlTableRender[Any]] =
    inline erasedValue[A] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        val headTypeClass =
          summonInline[HtmlTableRender[
            head
          ]] // summon was known as implicitly in scala 2
        val tailTypeClasses =
          getTypeclassInstances[tail] // recursive call to resolve also the tail
        headTypeClass
          .asInstanceOf[HtmlTableRender[Any]] :: getTypeclassInstances[tail]

// helper method like before
  protected inline def summonInstancesHelper[A](using m: Mirror.Of[A]) =
    getTypeclassInstances[m.MirroredElemTypes]

  // this traits can just be copy/pasted or reside in a library
  protected trait EasyDerive[TC[_]]:
    final def apply[A](using tc: TC[A]): TC[A] = tc

    case class CaseClassElement[A, B](
        label: String,
        typeclass: TC[B],
        getValue: A => B,
        idx: Int
    )
    case class CaseClassType[A](
        label: String,
        elements: List[CaseClassElement[A, ?]],
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
        elements: List[SealedElement[A, ?]],
        getElement: A => SealedElement[A, ?]
    )

    protected inline def getInstances[A <: Tuple]: List[TC[Any]] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  =>
          summonInline[TC[t]].asInstanceOf[TC[Any]] :: getInstances[ts]

    protected inline def getElemLabels[A <: Tuple]: List[String] =
      inline erasedValue[A] match
        case _: EmptyTuple => Nil
        case _: (t *: ts)  => constValue[t].toString :: getElemLabels[ts]

    def deriveCaseClass[A <: Product](caseClassType: CaseClassType[A]): TC[A]

    inline given derived[A <: Product](using m: Mirror.Of[A]): TC[A] =
      val label = constValue[m.MirroredLabel]
      val elemInstances = getInstances[m.MirroredElemTypes]
      val elemLabels = getElemLabels[m.MirroredElemLabels]
      inline m match
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
          val fromElements: List[Any] => A = elements =>
            @nowarn
            val product: Product = new Product:
              override def productArity: Int = caseClassElements.size

              override def productElement(n: Int): Any = elements(n)

              override def canEqual(that: Any): Boolean = false
            p.fromProduct(product)
          deriveCaseClass(
            CaseClassType[A](label, caseClassElements, fromElements)
          )
      end match
    end derived
  end EasyDerive

  /** Implement this trait for any type you want to render as part of an html table. See the concrete examples below.
    */
  trait HtmlTableRender[A]:
    def tableRow(a: A): scalatags.Text.TypedTag[String] = ???
    def tableCell(a: A): scalatags.Text.TypedTag[String] = ???
    def tableHeader(a: A): scalatags.Text.TypedTag[String] = ???
  end HtmlTableRender

  object HtmlTableRender extends EasyDerive[HtmlTableRender]:
    override def deriveCaseClass[A <: Product](
        productType: CaseClassType[A]
    ): HtmlTableRender[A] = new HtmlTableRender[A]:
      override def tableHeader(a: A) = ???
      // The dream, would be for this method to embed a table in a table - i.e. be able to render "compound products"
      override def tableCell(a: A) =
        // val b = a.asInstanceOf[Product]
        // val h    = b.productElementNames.toList
        // val header = tr(h.map(th(_)))
        // val rows = tableDeriveInstance.tableRow(a)
        // table(tbody(header,rows))
        a match
          case p: Product =>
            td(HtmlRenderer(p, false)(using this))
          // case q: Seq[Product] =>
          //   scautable(q)(using this)

      override def tableRow(a: A): scalatags.Text.TypedTag[String] =
        // println("table row in pretty string")
        if productType.elements.isEmpty then tr("empty")
        else
          val prettyElements =
            productType.elements.map(p => p.typeclass.tableCell(p.getValue(a)))
          tr(
            prettyElements
          )
  end HtmlTableRender

  given stringT: HtmlTableRender[String] = new HtmlTableRender[String]:
    override def tableCell(a: String) = td(a)

  given intT: HtmlTableRender[Int] = new HtmlTableRender[Int]:
    override def tableCell(a: Int) = td(a)

  given longT: HtmlTableRender[Long] = new HtmlTableRender[Long]:
    override def tableCell(a: Long) = td(s"$a")

  given doubleT: HtmlTableRender[Double] = new HtmlTableRender[Double]:
    override def tableCell(a: Double) = td(
      s"$a"
    )

  given enumT[E <: scala.reflect.Enum](using m: Mirror.SumOf[E]): HtmlTableRender[E] = new HtmlTableRender[E]:
    override def tableCell(a: E) = td(
      s"${a.toString}"
    )

  given nt[K <: Tuple, N <: Tuple]: HtmlTableRender[NamedTuple[K, N]] = new HtmlTableRender[NamedTuple[K, N]]:
    override def tableHeader(a: NamedTuple[K, N]): TypedTag[String] =
      val h = a.toTuple.productElementNames.toList
      tr(h.map(th(_)))
    end tableHeader
    override def tableCell(a: NamedTuple[K, N]) = td(
      s"$a"
    )

    override def tableRow(a: NamedTuple[K, N]): TypedTag[String] =
      val h = a.toTuple.productElementNames.toList
      val elems = a.toTuple.productIterator.toList
      val cells = elems.map(e => td(e.toString))
      tr(cells)
    end tableRow

  given booleanT: HtmlTableRender[Boolean] = new HtmlTableRender[Boolean]:
    override def tableCell(a: Boolean) = td(
      s"$a"
    )

  given optT[A](using inner: HtmlTableRender[A]): HtmlTableRender[Option[A]] = new HtmlTableRender[Option[A]]:
    override def tableCell(a: Option[A]) =
      a match
        case None           => td("")
        case Some(aContent) => inner.tableCell(aContent)

  given seqT[A](using inner: HtmlTableRender[A]): HtmlTableRender[Seq[A]] = new HtmlTableRender[Seq[A]]:
    override def tableCell(a: Seq[A]) =
      if a.isEmpty then td()
      else
        a.head match
          case p: Product =>
            val i = summon[HtmlTableRender[A]]
            val h = p.productElementNames.toList
            val header = tr(h.map(th(_)))
            val rows = a.map(in => i.tableRow(in))
            td(table(thead(header), tbody(rows)))
          case _ =>
            val cells = a.map(in => tr(inner.tableCell(in)))
            td(table(tbody(cells)))

  protected def deriveTableRow[A](a: A)(using instance: HtmlTableRender[A]) =
    instance.tableRow(a)

  protected def deriveTableHeader[A](a: A)(using instance: HtmlTableRender[A]) =
    tr(instance.tableRow(a))
  end deriveTableHeader

  // protected inline def getElemLabels[A <: Tuple]: List[String] =
  //   inline erasedValue[A] match
  //     case _: EmptyTuple => Nil // stop condition - the tuple is empty
  //     case _: (head *: tail) => // yes, in scala 3 we can match on tuples head and tail to deconstruct them step by step
  //       val headElementLabel =
  //         constValue[head].toString // bring the head label to value space
  //       val tailElementLabels =
  //         getElemLabels[tail] // recursive call to get the labels from the tail
  //       headElementLabel :: tailElementLabels // concat head + tail

  protected inline def getElemLabels[A <: Tuple]: List[String] =
    inline erasedValue[A] match
      case _: EmptyTuple     => Nil // stop condition - the tuple is empty
      case _: (head *: tail) => // yes, in scala 3 we can match on tuples head and tail to deconstruct them step by step
        val headElementLabel =
          constValue[head].toString // bring the head label to value space
        val tailElementLabels =
          getElemLabels[tail] // recursive call to get the labels from the tail
        headElementLabel :: tailElementLabels // concat head + tail

  protected inline def tableHeader[A](using m: Mirror.Of[A]) =
    val elemLabels = getElemLabels[m.MirroredElemLabels]
    tr(elemLabels.map(th(_)))
  end tableHeader

  @nowarn
  protected inline def deriveCaseClass[A](using m: Mirror.ProductOf[A]) =

    new HtmlTableRender[A]:

      override def tableHeader(a: A) =
        val elemLabels = getElemLabels[m.MirroredElemLabels]
        tr(elemLabels.map(th(_)))
      end tableHeader

      override def tableCell(a: A) = ???

      override def tableRow(a: A) =
        val elemLabels = getElemLabels[m.MirroredElemLabels]
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
      end tableRow

    /** Render a sequence of unknown type as an html table
      *
      * @param a
      *   \- A sequence of unknown type you wish to render as an html table
      * @param addHeader
      *   \- If true, add a header row to the table
      * @param tableDeriveInstance
      *   \- An instance of HtmlTableRender for the type `A`
      */
  def apply[A <: Product](a: Seq[A])(using
      tableDeriveInstance: HtmlTableRender[A]
  ): TypedTag[String] =
    val h = a.head.productElementNames.toList
    apply(a, true, h)
  end apply

  def apply[A <: Product](a: Seq[A], addHeader: Boolean = true, h: List[String])(using
      tableDeriveInstance: HtmlTableRender[A]
  ): TypedTag[String] =
    val header = tr(h.map(th(_)))
    val rows = for (r <- a) yield tableDeriveInstance.tableRow(r)
    if addHeader then table(thead(header), tbody(rows), id := "scautable", cls := "display")
    else table(thead(header), tbody(rows))
    end if
  end apply

  def apply[A <: Product](a: A, addHeader: Boolean)(using tableDeriveInstance: HtmlTableRender[A]): TypedTag[String] =
    apply(Seq(a), addHeader, a.productElementNames.toList)

  inline def nt[K <: Tuple, V <: Tuple](a: Seq[NamedTuple[K, V]])(using
      tableDeriveInstance: HtmlTableRender[V]
  ): TypedTag[String] =
    val names = constValueTuple[K].toList.map(_.toString())
    apply(a.map(_.toTuple), true, names)
  end nt

end HtmlRenderer
