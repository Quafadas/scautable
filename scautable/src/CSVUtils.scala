package io.github.quafadas.scautable

import scala.annotation.tailrec

private[scautable] object CSVUtils:
  def uniquifyHeaders(headers: List[String]): List[String] =
    if headers.toSet.sizeCompare(headers) == 0 then headers
    else
      val (newHeaders, _) = headers.foldLeft((List.empty[String], Map.empty[String, Int])):
        case ((acc, counter), elem) =>
          val (newElem, newCounter) = generateUniqueName(acc, counter, elem)
          (newElem :: acc) -> newCounter
      newHeaders.reverse

  @tailrec
  private def generateUniqueName(acc: List[String], counter: Map[String, Int], elem: String): (String, Map[String, Int]) =
    if acc.contains(elem) then
      val newCounter = counter.updatedWith(elem)(_.map(_ + 1).orElse(Some(1)))
      val newElem = s"${elem}_${newCounter(elem)}"
      generateUniqueName(acc, newCounter, newElem)
    else elem -> counter
end CSVUtils
