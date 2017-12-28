package com.github.mwegrz.scalautil.resource.sync

import java.util.concurrent.Executors

import com.typesafe.config.{ Config, ConfigFactory }

import language.implicitConversions
import java.sql.{ Connection, DatabaseMetaData, PreparedStatement, ResultSet }

import slick.jdbc.PositionedParameters
import javax.sql.DataSource
import com.github.mwegrz.scalautil.resource.SqlDatabase._

import akka.util.Timeout
import com.github.mwegrz.scalautil.resource.SqlDatabase.{ ResultSetIterator, Row, Sql }
import com.zaxxer.hikari.HikariDataSource

import scala.concurrent.ExecutionContext

class SqlDatabase private (_dataSource: HikariDataSource, maxPoolSize: Int) {
  private val timeoutEs = Executors.newFixedThreadPool(maxPoolSize)
  private implicit val timeoutEc = ExecutionContext.fromExecutor(timeoutEs)

  private[resource] def dataSource: DataSource = _dataSource

  def query[A](sql: Sql)(f: Row => A)(implicit timeout: Timeout): List[A] =
    withPreparedStatement(sql.queryParts.mkString) { ps =>
      val pp = new PositionedParameters(ps)
      sql.unitPConv(Unit, pp)
      val rs = withTimeout(ps, timeout)(_.executeQuery())
      new ResultSetIterator(rs, maxRows = 0, autoClose = true)(f).toList
    }(timeout)

  def query[A](sql: String, params: Seq[Option[Any]])(f: Row => A)(implicit timeout: Timeout): List[A] =
    withPreparedStatement(sql) { ps =>
      setParameters(ps, params)
      val rs = withTimeout(ps, timeout)(_.executeQuery())
      new ResultSetIterator(rs, maxRows = 0, autoClose = true)(f).toList
    }(timeout)

  def update(sql: Sql)(implicit timeout: Timeout): Int =
    withTransaction { conn =>
      withPreparedStatement(conn, sql.queryParts.mkString) { ps =>
        val pp = new PositionedParameters(ps)
        sql.unitPConv(Unit, pp)
        withTimeout(ps, timeout)(_.executeUpdate())
      }(timeout)
    }

  def update(sql: String, params: Seq[Option[Any]])(implicit timeout: Timeout): Int =
    withTransaction { conn =>
      withPreparedStatement(conn, sql) { ps =>
        setParameters(ps, params)
        withTimeout(ps, timeout)(_.executeUpdate())
      }(timeout)
    }

  def updateBatch(sql: String, params: Seq[Seq[Option[Any]]])(implicit timeout: Timeout): Array[Int] =
    withTransaction { conn =>
      withPreparedStatement(conn, sql) { ps =>
        params.foreach { p =>
          setParameters(ps, p)
          ps.addBatch()
        }
        withTimeout(ps, timeout)(_.executeBatch())
      }(timeout)
    }

  def withMetaData[A](f: DatabaseMetaData => A): A =
    withConnection { conn =>
      f(conn.getMetaData)
    }

  def queryMetaData[A](qf: DatabaseMetaData => ResultSet)(f: Row => A): List[A] =
    new ResultSetIterator[A](withMetaData(qf), maxRows = 0, autoClose = true)(f).toList

  def withConnection[A](f: Connection => A): A =
    arm.using(_dataSource.getConnection) { f }

  def withPreparedStatement[A](conn: Connection, sql: String)(f: PreparedStatement => A)(implicit timeout: Timeout): A =
    arm.using(conn.prepareStatement(sql)) { ps =>
      //ps.setQueryTimeout(timeout.toSeconds)
      f(ps)
    }

  def withPreparedStatement[A](sql: String)(f: PreparedStatement => A)(implicit timeout: Timeout): A =
    withConnection { conn =>
      withPreparedStatement(conn, sql)(f(_))(timeout)
    }

  def withTransaction[A](f: Connection => A): A =
    withConnection { conn =>
      conn.setAutoCommit(false)
      val r = f(conn)
      conn.commit()
      r
    }

  def shutdown() = {
    timeoutEs.shutdown()
    _dataSource.close()
  }
}

object SqlDatabase {

  /** Creates a [[SqlDatabase]] based on settings at the root path in the `config`. The settings will be stacked upon the
    * reference config and all default settings added.
    *
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabase]]
    */
  def forConfig(config: Config): SqlDatabase = {
    val mergedConfig = config.withFallback(defaultReference)
    createSqlDatabase(mergedConfig)
  }

  /** Creates a [[SqlDatabase]] based on settings at a `path` in the `config`. The settings will be stacked upon the
    * reference config and all default settings added.
    *
    * @param path a path in the `config` at which the configuration is defined
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabase]]
    */
  def forConfig(path: String, config: Config = ConfigFactory.load()): SqlDatabase = {
    val configAtPath = config.getConfig(path)
    val mergedConfig = configAtPath.withFallback(defaultReference)
    createSqlDatabase(mergedConfig)
  }

  /** Creates a [[SqlDatabase]] based on settings at the "sql-database" path in the `config`.
    *
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabase]]
    */
  def apply(config: Config = ConfigFactory.load()): SqlDatabase = createSqlDatabase(config.getConfig(DefaultConfigPath))

  /** Creates a [[SqlDatabase]] based on settings provided in the argument applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @param username overrides sql-database.username setting
    * @param password overrides sql-database.password setting
    * @param driverClassName overrides sql-database.driver-class-name setting
    * @return configured and initialized [[SqlDatabase]]
    */
  def apply(url: String,
            username: Option[String],
            password: Option[String],
            driverClassName: Option[String]): SqlDatabase = forConfig(ConfigFactory.parseString(s"""url = "$url"
       |username = "${username.getOrElse("")}"
       |password = "${password.getOrElse("")}"
       |driver-class-name = "${driverClassName.getOrElse("")}"
    """.stripMargin))

  /** Creates a [[SqlDatabase]] based on settings provided in the arguments applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @param username overrides sql-database.username setting
    * @param password overrides sql-database.password setting
    * @return configured and initialized [[SqlDatabase]]
    */
  def apply(url: String, username: Option[String], password: Option[String]): SqlDatabase =
    apply(url, username, password, None)

  /** Creates a [[SqlDatabase]] based on settings provided in the argument applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @return configured and initialized [[SqlDatabase]]
    */
  def apply(url: String): SqlDatabase = apply(url, None, None, None)

  private def createSqlDatabase(config: Config): SqlDatabase = {

    val maxPoolSize = config.getInt("max-pool-size")

    new SqlDatabase(createDataSource(config), maxPoolSize)
  }
}
