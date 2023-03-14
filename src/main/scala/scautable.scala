//> using lib "org.scalameta::munit:1.0.0-M7"

package object scautable {
  def apply() = "hi"
}

@main def runSomething = println(scautable())