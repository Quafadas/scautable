package io.github.quafadas.scautable.scalasql

import scalasql.core.DbApi

/** A [[DbApi]] that defers opening its underlying connection until the first call is made on it.
  *
  * Mirrors `DbIterator`'s lazy auto-connect: constructing one (e.g. via `DB.sqlTable`) never
  * touches the network, so it's safe to hand back even when no live connection is configured
  * (compile-time-only schema inference, tests that never execute the query, etc.).
  */
final class LazyDbApi(open: () => DbApi) extends DbApi:
  private lazy val impl: DbApi = open()
  export impl.*
end LazyDbApi
