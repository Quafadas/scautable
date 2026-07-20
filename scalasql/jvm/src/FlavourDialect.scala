package io.github.quafadas.scautable.scalasql

import scalasql.core.DialectConfig
import io.github.quafadas.scautable.db.{DbFlavour, H2, Postgres}

/** Maps a [[DbFlavour]] marker type to its scalasql dialect.
  *
  * `DB.sqlTable[F]` uses this to build a live `DbApi` from env-var connection parameters
  * without the caller having to import or wire up the dialect by hand.
  */
trait FlavourDialect[F <: DbFlavour]:
  def dialect: DialectConfig

object FlavourDialect:
  given FlavourDialect[H2] with
    def dialect: DialectConfig = scalasql.H2Dialect

  given FlavourDialect[Postgres] with
    def dialect: DialectConfig = scalasql.PostgresDialect
end FlavourDialect
