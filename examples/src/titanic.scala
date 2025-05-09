import io.github.quafadas.table.*

@main def titanic =

  val titanic = CSV.resource("titanic.csv").toSeq
  titanic.take(10).ptbln
end titanic
