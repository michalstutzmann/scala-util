package com.github.mwegrz.scalautil.sqldatabase

import java.io.InputStream
import java.sql.{
  BatchUpdateException,
  Connection,
  DatabaseMetaData,
  PreparedStatement,
  ResultSet,
  SQLException,
  SQLTimeoutException,
  Types
}
import java.time.{ LocalDate, LocalDateTime, LocalTime, ZoneId }
import java.util.Properties
import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }

import akka.util.Timeout
import com.github.mwegrz.scalautil.arm
import com.typesafe.config.{ Config, ConfigFactory }
import com.zaxxer.hikari.HikariDataSource
import org.reactivestreams.Publisher
import slick.driver.JdbcDriver
import slick.jdbc.JdbcBackend.Database
import slick.jdbc._
import slick.util.{ AsyncExecutor, CloseableIterator, ReadAheadIterator }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

/** Asynchronous JDBC-based database manager which can be used for plain SQL queries and updates (DML and DDL operations)
  * backed by Slick and HakiriCP connection pool.
  *
  * Note: Slick and HakiriCP dependencies are not transitive and
  * have to be explicitly added to the classpath.
  */
class SqlDatabaseClient private (database: Database, maxPoolSize: Int) {
  import SqlDatabaseClient._
  import SqlDatabaseClient.Implicits._
  import SqlDatabaseClient.driver.api._

  private val timeoutEs = Executors.newFixedThreadPool(maxPoolSize)
  private implicit val timeoutEc = ExecutionContext.fromExecutor(timeoutEs)

  def queryAsync[A](sql: Sql)(f: Row => A)(implicit timeout: Timeout): Future[List[A]] = {
    val a = new SimpleJdbcAction[List[A]](ctx => {
      withPreparedStatement(ctx.connection, sql.queryParts.mkString) { ps =>
        val pp = new PositionedParameters(ps)
        sql.unitPConv((), pp)
        val rs = withTimeout(ps, timeout)(_.executeQuery())
        new ResultSetIterator(rs, maxRows = 0, autoClose = true)(f).toList
      }
    })
    //database.run(sql.as[A](GetResult(r => retrieveRow(r.rs)(f))).withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(a)
  }

  def queryAsync[A](sql: String, params: Seq[Option[Any]] = Nil)(
      f: Row => A
  )(implicit timeout: Timeout): Future[List[A]] = {
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
        override def iteratorTo(
            maxRows: Int
        )(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
          val ps = session.conn.prepareStatement(sql.queryParts.mkString)
          val pp = new PositionedParameters(ps)
          sql.unitPConv((), pp)
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
  def queryStream[A](sql: String, params: Seq[Option[Any]] = Nil)(
      f: Row => A
  )(implicit timeout: Timeout): Publisher[A] = {
    val a = new StreamingInvokerAction[Vector[A], A, Effect] {
      override def statements = Nil
      override def createBuilder = Vector.newBuilder[A]
      override def createInvoker(statements: Iterable[String]) = new Invoker[A] {
        override def iteratorTo(
            maxRows: Int
        )(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
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

  def updateAsync(sql: Sql)(implicit timeout: Timeout): Future[Int] = {
    val a = new SimpleJdbcAction[Int](c => {
      withPreparedStatement(c.connection, sql.queryParts.mkString) { ps =>
        val pp = new PositionedParameters(ps)
        sql.unitPConv((), pp)
        withTimeout(ps, timeout)(_.executeUpdate())
      }
    })
    //database.run(sql.asUpdate.transactionally.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(a.transactionally)
  }

  def updateAsync(sql: String, params: Seq[Option[Any]] = Nil)(
      implicit timeout: Timeout
  ): Future[Int] = {
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
          sql.unitPConv((), pp)
          withTimeout(ps, timeout)(_.executeUpdate())
        }
      })
    }
    //database.run(DBIO.seq(sqls.map(_.asUpdate): _*).transactionally.withPinnedSession.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt)))
    database.run(DBIO.seq(as: _*).transactionally.withPinnedSession)
  }

  def updateBatch(sql: String, params: Seq[Seq[Option[Any]]] = Nil)(
      implicit timeout: Timeout
  ): Future[Array[Int]] = {
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
        override def iteratorTo(
            maxRows: Int
        )(implicit session: JdbcBackend#SessionDef): CloseableIterator[A] = {
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

object SqlDatabaseClient {

  /** Creates a [[SqlDatabaseClient]] based on settings at the root path in the `config`. The settings will be stacked upon the
    * reference config and all default settings added.
    *
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def forConfig(config: Config): SqlDatabaseClient = {
    val mergedConfig = config.withFallback(defaultReference)
    createSqlDatabase(mergedConfig)
  }

  /** Creates a [[SqlDatabaseClient]] based on settings at a `path` in the `config`. The settings will be stacked upon the
    * reference config and all default settings added.
    *
    * @param path a path in the `config` at which the configuration is defined
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def forConfig(path: String, config: Config = ConfigFactory.load()): SqlDatabaseClient = {
    val configAtPath = config.getConfig(path)
    val mergedConfig = configAtPath.withFallback(defaultReference)
    createSqlDatabase(mergedConfig)
  }

  /** Creates a [[SqlDatabaseClient]] based on settings in the `config`.
    *
    * @param config optional custom [[Config]] in which the configuration is defined.
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def apply(config: Config = ConfigFactory.load()): SqlDatabaseClient =
    createSqlDatabase(config)

  /** Creates a [[SqlDatabaseClient]] based on settings provided in the arguments applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @param username overrides sql-database.username setting
    * @param password overrides sql-database.password setting
    * @param driverClassName overrides sql-database.driver-class-name setting
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def apply(
      url: String,
      username: Option[String],
      password: Option[String],
      driverClassName: Option[String]
  ): SqlDatabaseClient =
    forConfig(ConfigFactory.parseString(s"""url = "$url"
       |username = "${username.getOrElse("")}"
       |password = "${password.getOrElse("")}"
       |driver-class-name = "${driverClassName.getOrElse("")}"
    """.stripMargin))

  /** Creates a [[SqlDatabaseClient]] based on settings provided in the arguments applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @param username overrides sql-database.username setting
    * @param password overrides sql-database.password setting
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def apply(url: String, username: Option[String], password: Option[String]): SqlDatabaseClient =
    apply(url, username, password, None)

  /** Creates a [[SqlDatabaseClient]] based on settings provided in the argument applied over the reference config.
    *
    * @param url overrides sql-database.url setting
    * @return configured and initialized [[SqlDatabaseClient]]
    */
  def apply(url: String): SqlDatabaseClient = apply(url, None, None, None)

  private def createSqlDatabase(config: Config): SqlDatabaseClient = {

    val threadPoolSize = config.getInt("async-executor.thread-pool-size")
    val maxPoolSize = threadPoolSize
    val minIdle = maxPoolSize
    val updatedConf = ConfigFactory
      .parseMap(
        Map(
          "async-executor.thread-pool-size" -> new Integer(threadPoolSize),
          "max-pool-size" -> new Integer(maxPoolSize)
        ).asJava
      )
      .withFallback(config)

    val queueSize = config.getString("async-executor.queue-size") match {
      case "unlimited" => -1
      case _           => config.getInt("async-executor.queue-size")
    }

    new SqlDatabaseClient(
      Database.forSource(
        new JdbcDataSource {
          val ds = createDataSource(updatedConf)

          override def createConnection(): Connection = ds.getConnection

          override def close(): Unit = ds.close()

          override val maxConnections: Option[Int] = None
        },
        AsyncExecutor(
          name = config.getString("async-executor.name"),
          numThreads = threadPoolSize,
          queueSize = queueSize
        )
      ),
      maxPoolSize
    )
  }

  type Sql = SQLActionBuilder

  type SqlInterpolation = ActionBasedSQLInterpolation

  trait Row {

    def getBooleanOption(name: String): Option[Boolean]

    def getBoolean(name: String): Boolean = getBooleanOption(name).get

    def getByteOption(name: String): Option[Byte]

    def getByte(name: String): Byte = getByteOption(name).get

    def getShortOption(name: String): Option[Short]

    def getShort(name: String): Short = getShortOption(name).get

    def getIntOption(name: String): Option[Int]

    def getInt(name: String): Int = getIntOption(name).get

    def getLongOption(name: String): Option[Long]

    def getLong(name: String): Long = getLongOption(name).get

    def getFloatOption(name: String): Option[Float]

    def getFloat(name: String): Float = getFloatOption(name).get

    def getDoubleOption(name: String): Option[Double]

    def getDouble(name: String): Double = getDoubleOption(name).get

    def getBigDecimalOption(name: String): Option[BigDecimal]

    def getBigDecimal(name: String): BigDecimal = getBigDecimalOption(name).get

    def getStringOption(name: String): Option[String]

    def getString(name: String): String = getStringOption(name).get

    def getLocalDateOption(name: String): Option[LocalDate]

    def getLocalDate(name: String): LocalDate = getLocalDateOption(name).get

    def getLocalTimeOption(name: String): Option[LocalTime]

    def getLocalTime(name: String): LocalTime = getLocalTimeOption(name).get

    def getLocalDateTimeOption(name: String): Option[LocalDateTime]

    def getLocalDateTimeOption(name: String, timeZoneId: ZoneId): Option[LocalDateTime]

    def getLocalDateTime(name: String): LocalDateTime = getLocalDateTimeOption(name).get

    def getLocalDateTime(name: String, timeZoneId: ZoneId): LocalDateTime =
      getLocalDateTimeOption(name, timeZoneId).get

    def getByteArrayOption(name: String): Option[Array[Byte]]

    def getByteArray(name: String): Array[Byte] = getByteArrayOption(name).get

    def withBlobOptionInputStream[A](name: String)(f: InputStream => A): Option[A]

    def withBlobInputStream[A](name: String)(f: InputStream => A): A =
      withBlobOptionInputStream(name)(f).get

    def withClobOptionInputStream[A](name: String)(f: InputStream => A): Option[A]

    def withClobInputStream[A](name: String)(f: InputStream => A): A =
      withClobOptionInputStream(name)(f).get

  }

  object Implicits {

    implicit def sqlInterpolation(s: StringContext): SqlInterpolation =
      driver.api.actionBasedSQLInterpolation(s)

    implicit class SqlOps(builder: Sql) {

      def stripMargin(marginChar: Char): Sql =
        builder.copy(builder.queryParts.map(_.asInstanceOf[String].stripMargin(marginChar)))

      def stripMargin: Sql =
        builder.copy(builder.queryParts.map(_.asInstanceOf[String].stripMargin))

    }

    implicit class ResultSetOps(rs: ResultSet) extends Row {

      override def getBooleanOption(name: String): Option[Boolean] = Option(rs.getBoolean(name))

      override def getByteOption(name: String): Option[Byte] = Option(rs.getByte(name))

      override def getShortOption(name: String): Option[Short] = Option(rs.getShort(name))

      override def getIntOption(name: String): Option[Int] = Option(rs.getInt(name))

      override def getLongOption(name: String): Option[Long] = Option(rs.getLong(name))

      override def getFloatOption(name: String): Option[Float] = Option(rs.getFloat(name))

      override def getDoubleOption(name: String): Option[Double] = Option(rs.getDouble(name))

      override def getBigDecimalOption(name: String): Option[BigDecimal] =
        Option(rs.getBigDecimal(name))

      override def getStringOption(name: String): Option[String] = Option(rs.getString(name))

      override def getLocalDateOption(name: String): Option[LocalDate] =
        Option(rs.getDate(name)).map(_.toLocalDate)

      override def getLocalTimeOption(name: String): Option[LocalTime] =
        Option(rs.getTime(name)).map(_.toLocalTime)

      override def getLocalDateTimeOption(name: String): Option[LocalDateTime] =
        getLocalDateTimeOption(name, ZoneId.systemDefault())

      override def getLocalDateTimeOption(name: String, timeZoneId: ZoneId): Option[LocalDateTime] =
        Option(rs.getTimestamp(name)).map { ts => LocalDateTime.ofInstant(ts.toInstant, timeZoneId) }

      override def getByteArrayOption(name: String): Option[Array[Byte]] = Option(rs.getBytes(name))

      override def withBlobOptionInputStream[A](name: String)(f: InputStream => A): Option[A] =
        Option(rs.getBlob(name)).map { b => arm.using(b.getBinaryStream) { f } }

      override def withClobOptionInputStream[A](name: String)(f: InputStream => A): Option[A] =
        Option(rs.getClob(name)).map { b => arm.using(b.getAsciiStream) { f } }
    }
  }

  private[sqldatabase] val DefaultConfigPath = "sql-database-client"

  private[sqldatabase] val defaultReference =
    ConfigFactory.defaultReference.getConfig(DefaultConfigPath)

  private[sqldatabase] val driver = new JdbcDriver {}

  private[sqldatabase] class ResultSetIterator[+A](rs: ResultSet, maxRows: Int, autoClose: Boolean)(
      f: Row => A
  ) extends ReadAheadIterator[A]
      with CloseableIterator[A] {

    import Implicits.ResultSetOps

    private var closed = false
    private var readRows = 0
    private val md = rs.getMetaData
    private val rp = Seq.range(1, rs.getMetaData.getColumnCount + 1)

    // Set fetch size
    {
      val rowDataDisplaySize = rp.map(md.getColumnDisplaySize).sum
      if (rowDataDisplaySize > 0) {
        // Rough estimate of the fetch size. See http://stackoverflow.com/questions/9220171/setting-oracle-size-of-row-fetches-higher-makes-my-app-slower
        val fetchSize = (4 * 1024 * 1024) / rowDataDisplaySize
        rs.setFetchSize(fetchSize)
      }
    }

    protected def fetchNext(): A = {
      if ((readRows < maxRows || maxRows <= 0) && rs.next()) {
        val res = f(rs)
        readRows += 1
        res
      } else {
        if (autoClose) close()
        finished()
      }
    }

    final def close(): Unit = {
      if (!closed) {
        rs.close()
        closed = true
      }
    }
  }

  private[sqldatabase] def setParameters(ps: PreparedStatement, params: Seq[Option[Any]]): Unit = {
    params.zipWithIndex foreach {
      case (None, index)        => ps.setNull(index + 1, Types.OTHER)
      case (Some(param), index) => ps.setObject(index + 1, param)
    }
  }

  private[sqldatabase] def withTimeout[A](ps: PreparedStatement, timeout: Timeout)(
      f: PreparedStatement => A
  )(implicit ec: ExecutionContext): A = {
    val timedOut = new CountDownLatch(1)
    val cancellerDone = new CountDownLatch(1)
    val canceller = new Runnable {
      @volatile var finished = false
      @volatile var cancelled = false

      override def run() = {
        timedOut.await(timeout.duration.toMillis, TimeUnit.MILLISECONDS)
        /*ps.synchronized {
          ps.wait(timeout.duration.toMillis)
        }*/
        if (!finished) {
          cancelled = true
          ps.cancel()
        }
        cancellerDone.countDown()
      }
    }
    ec.execute(canceller)
    val result =
      try {
        f(ps)
      } catch {
        case ex: SQLException =>
          if (canceller.cancelled) throw new SQLTimeoutException() else throw ex
      } finally {
        canceller.finished = true
        timedOut.countDown()
        //ps.synchronized(ps.notifyAll())
        cancellerDone.await()
      }
    if (canceller.cancelled) throw new SQLTimeoutException()
    result
  }

  private[sqldatabase] def withPreparedStatement[A](conn: java.sql.Connection, sql: String)(
      f: PreparedStatement => A
  ): A =
    arm.using(conn.prepareStatement(sql)) { ps =>
      //ps.setQueryTimeout(timeout.toSeconds)
      f(ps)
    }

  private[sqldatabase] def createDataSource(config: Config): HikariDataSource = {
    // This verifies that the Config is sane and has our
    // reference config. Importantly, we specify the "sql-database"
    // path so we only validate settings that belong to this
    // library. Otherwise, we might throw mistaken errors about
    // settings we know nothing about.
    config.checkValid(ConfigFactory.defaultReference().getConfig("sql-database-client"))

    val ds = new HikariDataSource()

    ds.setJdbcUrl(config.getString("url"))
    if (config.getString("username") != "") ds.setUsername(config.getString("username"))
    if (config.getString("password") != "") ds.setPassword(config.getString("password"))
    ds.setAutoCommit(config.getBoolean("auto-commit"))
    ds.setConnectionTimeout(config.getDuration("connection-timeout", TimeUnit.MILLISECONDS))
    ds.setIdleTimeout(config.getDuration("idle-timeout", TimeUnit.MILLISECONDS))
    ds.setMaxLifetime(config.getDuration("max-lifetime", TimeUnit.MILLISECONDS))
    ds.setConnectionTestQuery(config.getString("connection-test-query"))

    val maxPoolSize = config.getInt("max-pool-size")

    val minIdle = config.getString("min-idle") match {
      case "unlimited" => maxPoolSize
      case _           => config.getInt("min-idle")
    }

    ds.setMaximumPoolSize(maxPoolSize)
    ds.setMinimumIdle(minIdle)
    ds.setPoolName(config.getString("pool-name"))
    //ds.setInitializationFailFast(config.getBoolean("initialization-fail-fast"))
    ds.setIsolateInternalQueries(config.getBoolean("isolate-internal-queries"))
    ds.setAllowPoolSuspension(config.getBoolean("allow-pool-suspension"))
    ds.setReadOnly(config.getBoolean("read-only"))
    if (config.getString("catalog") != "") ds.setCatalog(config.getString("catalog"))
    if (config.getString("connection-init-sql") != "")
      ds.setConnectionInitSql(config.getString("connection-init-sql"))
    if (config.getString("driver-class-name") != "")
      ds.setDriverClassName(config.getString("driver-class-name"))
    if (config.getString("transaction-isolation") != "")
      ds.setTransactionIsolation(config.getString("transaction-isolation"))
    ds.setValidationTimeout(config.getDuration("validation-timeout", TimeUnit.MILLISECONDS))
    ds.setLeakDetectionThreshold(
      config.getDuration("leak-detection-threshold", TimeUnit.MILLISECONDS)
    )

    val props: Properties = new Properties()
    config.getConfig("properties").entrySet().asScala.foreach { a => props.put(a.getKey, a.getValue.render()) }
    ds.setDataSourceProperties(props)
    // TODO
    //ds.setScheduledExecutorService()

    ds
  }
}
