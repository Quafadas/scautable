package io.github.quafadas.scautable


trait Decoder[T]:
  def decode(str: String): Option[T]

object Decoder:
  inline given Decoder[Int] with
    def decode(str: String): Option[Int] = str.toIntOption

  inline given Decoder[Long] with
    def decode(str: String): Option[Long] = str.toLongOption

  inline given Decoder[Double] with
    def decode(str: String): Option[Double] = str.toDoubleOption

  inline given Decoder[Boolean] with
    def decode(str: String): Option[Boolean] =
      str.toLowerCase match
        case "true"  => Some(true)
        case "false" => Some(false)
        case "0" => Some(false)
        case "1" => Some(true)
        case _       => None

  inline given Decoder[String] with
    def decode(str: String): Option[String] = Some(str)

  inline given [T](using d: Decoder[T]): Decoder[Option[T]] with
    def decode(str: String): Option[Option[T]] =
      if str.isEmpty then Some(None)
      else d.decode(str).map(Some(_))
