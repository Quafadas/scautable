package io.github.quafadas.scautable.scalasql

import scalasql.core.DialectConfig
import io.github.quafadas.scautable.db.{DbFlavour, H2, MsSqlServer, Postgres}

/** Maps a [[DbFlavour]] marker type to its scalasql dialect.
  *
  * `DB.sqlTable[F]` uses this to build a live `DbApi` from env-var connection parameters without the caller having to import or wire up the dialect by hand.
  */
trait FlavourDialect[F <: DbFlavour]:
  def dialect: DialectConfig
end FlavourDialect

object FlavourDialect:
  given FlavourDialect[H2] with
    def dialect: DialectConfig = scalasql.H2Dialect
  end given

  given FlavourDialect[Postgres] with
    def dialect: DialectConfig = scalasql.PostgresDialect
  end given

  given FlavourDialect[MsSqlServer] with
    def dialect: DialectConfig = scalasql.MsSqlDialect
  end given
end FlavourDialect
