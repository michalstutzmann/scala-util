package com.github.mwegrz.scalautil.resource.async

import java.sql.{ BatchUpdateException, Connection, DatabaseMetaData, ResultSet, SQLException }
import java.util.concurrent.{ Executor, ExecutorService, Executors }

import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }

import language.implicitConversions
import slick.jdbc._
import com.github.mwegrz.scalautil.resource.SqlDatabase.Implicits._
import com.github.mwegrz.scalautil.resource.SqlDatabase.driver.api._
import com.github.mwegrz.scalautil.resource.SqlDatabase._
import org.reactivestreams.Publisher
import slick.util.CloseableIterator
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._

/** Asynchronous JDBC-based database manager which can be used for plain SQL queries and updates (DML and DDL operations)
  * backed by Slick and HakiriCP connection pool.
  *
  * Note: Slick and HakiriCP dependencies are not transitive and
  * have to be explicitly added to the classpath.
  */
class SqlDatabase private (database: Database, maxPoolSize: Int) {

  private val timeoutEs = Executors.newFixedThreadPool(maxPoolSize)
  private implicit val timeoutEc = ExecutionContext.fromExecutor(timeoutEs)

  def query[A](sql: Sql)(f: Row => A)(implicit timeout: Timeout): Future[List[A]] = {
    val a = new SimpleJdbcAction[List[A]](ctx => {
      withPreparedStatement(ctx.connection, sql.queryParts.mkString) { ps =>
        val pp = new PositionedParameters(ps)
        sql.unitPConv(Unit, pp)
        val rs = withTimeout(ps, timeout)(_.executeQuery())
        new ResultSetIterator(rs, maxRows = 0, autoClose = true)(f).toList
      }
    })
    //database.run(sql.as[A](GetResult(r => retrieveRow(r.rs)(f))).withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(a)
  }

  def query[A](sql: String, params: Seq[Option[Any]] = Nil)(f: Row => A)(implicit timeout: Timeout): Future[List[A]] = {
    val a = new SimpleJdbcAction[List[A]](ctx => {
      withPreparedStatement(ctx.connection, sql) { ps =>
        setParameters(ps, params)
        val rs = withTimeout(ps, timeout)(_.executeQuery())
        new ResultSetIterator(rs, maxRows = 0, autoClose = true)(f).toList
      }
    })
    database.run(a)
  }

  /** Create a [[Publisher]] for Reactive Streams which, when subscribed to, will run the specified
    * SQL query and return the result directly as a stream without buffering everything first.
    * This method is only supported for streaming actions.
    *
    * The query does not actually start to run until the call to `onSubscribe` returns, after
    * which the Subscriber is responsible for reading the full response or cancelling the
    * Subscription. The created Publisher can be reused to serve a multiple Subscribers,
    * each time triggering a new execution of the query.
    *
    * All `onNext` calls are done synchronously and the ResultSet row
    * is not advanced before `onNext` returns. This allows the Subscriber to access LOB pointers
    * from within `onNext`. If streaming is interrupted due to back-pressure signaling, the next
    * row will be prefetched (in order to buffer the next result page from the server when a page
    * boundary has been reached).
    *
    * @param sql Interpolated SQL query (See [[sqlInterpolation]]]).
    * @tparam A  Resulting row type.
    * @param f   The mapping from [[java.sql.ResultSet]] to the desired `A` resulting row type.
    * @return    Reactive Streams publisher.
    */
  def queryStream[A](sql: Sql)(f: Row => A)(implicit timeout: Timeout): Publisher[A] = {
    val a = new StreamingInvokerAction[Vector[A], A, Effect] {
      override def statements = Nil
      override def createBuilder = Vector.newBuilder[A]
      override def createInvoker(statements: Iterable[String]) = new Invoker[A] {
        override def iteratorTo(maxRows: Int)(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
          val ps = session.conn.prepareStatement(sql.queryParts.mkString)
          val pp = new PositionedParameters(ps)
          sql.unitPConv(Unit, pp)
          //ps.setQueryTimeout(timeout.toSeconds)
          val rs = withTimeout(ps, timeout)(_.executeQuery())
          new ResultSetIterator[A](rs, maxRows, autoClose = true)(f)
        }
      }
    }
    //database.stream(sql.as[A](GetResult(r => retrieveRow(r.rs)(f))).withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds)))
    database.stream(a)
  }

  /** Same as [[queryStream[A](sql: Sql)(implicit f: ResultSet => A)]] but takes a standard JDBC SQL string with `?` placeholders
    * and params instead of a interpolated SQL query.
    *
    * @param sql    Standard JDBC SQL query with `?` placeholders.
    * @param params Values for the `?` placeholders in the same order as they appear in the query.
    * @tparam A  Resulting row type.
    * @param f   The mapping from [[java.sql.ResultSet]] to the desired `A` resulting row type.
    * @return    Reactive Streams publisher.
    */
  def queryStream[A](sql: String, params: Seq[Option[Any]] = Nil)(f: Row => A)(
      implicit timeout: Timeout): Publisher[A] = {
    val a = new StreamingInvokerAction[Vector[A], A, Effect] {
      override def statements = Nil
      override def createBuilder = Vector.newBuilder[A]
      override def createInvoker(statements: Iterable[String]) = new Invoker[A] {
        override def iteratorTo(maxRows: Int)(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
          val ps = session.conn.prepareStatement(sql)
          setParameters(ps, params)
          //ps.setQueryTimeout(timeout.toSeconds)
          val rs = withTimeout(ps, timeout)(_.executeQuery())
          new ResultSetIterator[A](rs, maxRows, autoClose = true)(f)
        }
      }
    }
    database.stream(a)
  }

  def update(sql: Sql)(implicit timeout: Timeout): Future[Int] = {
    val a = new SimpleJdbcAction[Int](c => {
      withPreparedStatement(c.connection, sql.queryParts.mkString) { ps =>
        val pp = new PositionedParameters(ps)
        sql.unitPConv(Unit, pp)
        withTimeout(ps, timeout)(_.executeUpdate())
      }
    })
    //database.run(sql.asUpdate.transactionally.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(a.transactionally)
  }

  def update(sql: String, params: Seq[Option[Any]] = Nil)(implicit timeout: Timeout): Future[Int] = {
    val a = new SimpleJdbcAction[Int](c => {
      withPreparedStatement(c.connection, sql) { ps =>
        setParameters(ps, params)
        withTimeout(ps, timeout)(_.executeUpdate())
      }
    })
    database.run(a.transactionally.withPinnedSession)
  }

  def updateBatch(sqls: Seq[Sql])(implicit timeout: Timeout): Future[Unit] = {
    val as = sqls.map { sql =>
      new SimpleJdbcAction[Int](c => {
        withPreparedStatement(c.connection, sql.queryParts.mkString) { ps =>
          val pp = new PositionedParameters(ps)
          sql.unitPConv(Unit, pp)
          withTimeout(ps, timeout)(_.executeUpdate())
        }
      })
    }
    //database.run(DBIO.seq(sqls.map(_.asUpdate): _*).transactionally.withPinnedSession.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(DBIO.seq(as: _*).transactionally.withPinnedSession)
  }

  def updateBatch(sql: String, params: Seq[Seq[Option[Any]]] = Nil)(implicit timeout: Timeout): Future[Array[Int]] = {
    val a = new SimpleJdbcAction[Array[Int]](c => {
      withPreparedStatement(c.connection, sql) { ps =>
        params foreach { elem =>
          setParameters(ps, elem)
          ps.addBatch()
        }
        try {
          withTimeout(ps, timeout)(_.executeBatch())
        } catch {
          case ex: BatchUpdateException =>
            //TODO Add DB vendor specific logic (Oracle differs from H2 in counting updates)
            ex.getUpdateCounts
          case ex: SQLException => throw ex
        }
      }
    })
    database.run(a.transactionally.withPinnedSession)
  }

  def withMetaData[A](f: DatabaseMetaData => A): Future[A] = {
    val a = new SimpleJdbcAction[A](c => {
      f(c.session.metaData)
    })
    database.run(a)
  }

  def queryMetaData[A](qf: DatabaseMetaData => ResultSet)(f: Row => A): Future[List[A]] = {
    val a = new SimpleJdbcAction[List[A]](c => {
      new ResultSetIterator[A](qf(c.session.metaData), maxRows = 0, autoClose = true)(f).toList
    })
    database.run(a)
  }

  def queryMetaDataStream[A](qf: DatabaseMetaData => ResultSet)(f: Row => A): Publisher[A] = {
    val a = new StreamingInvokerAction[Vector[A], A, Effect] {
      override def statements = Nil
      override def createBuilder = Vector.newBuilder[A]
      override def createInvoker(statements: Iterable[String]) = new Invoker[A] {
        override def iteratorTo(maxRows: Int)(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
          new ResultSetIterator[A](qf(session.metaData), maxRows = 0, autoClose = true)(f)
        }
      }
    }
    database.stream(a)
  }

  def shutdown(): Future[Unit] = {
    database.shutdown.map(_ => timeoutEs.shutdown())
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

  /** Creates a [[SqlDatabase]] based on settings provided in the arguments applied over the reference config.
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

    val threadPoolSize = config.getInt("async-executor.thread-pool-size")
    val maxPoolSize = threadPoolSize
    val minIdle = maxPoolSize
    val updatedConf = ConfigFactory
      .parseMap(
        Map("async-executor.thread-pool-size" -> new Integer(threadPoolSize),
            "max-pool-size" -> new Integer(maxPoolSize)).asJava)
      .withFallback(config)

    val queueSize = config.getString("async-executor.queue-size") match {
      case "unlimited" => -1
      case _           => config.getInt("async-executor.queue-size")
    }

    new SqlDatabase(
      Database.forSource(
        new JdbcDataSource {
          val ds = createDataSource(updatedConf)

          override def createConnection(): Connection = ds.getConnection

          override def close(): Unit = ds.close()

          override val maxConnections: Option[Int] = None
        },
        AsyncExecutor(name = config.getString("async-executor.name"),
                      numThreads = threadPoolSize,
                      queueSize = queueSize)
      ),
      maxPoolSize
    )
  }

}
