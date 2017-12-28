package com.github.mwegrz.scalautil.resource

import java.io.InputStream
import java.sql.{ PreparedStatement, ResultSet, SQLException, SQLTimeoutException, Types }
import java.time._
import java.util.Properties
import java.util.concurrent.{ CountDownLatch, TimeUnit }

import akka.util.Timeout
import com.github.mwegrz.scalautil.resource.sync.arm
import com.typesafe.config.{ Config, ConfigFactory }
import com.zaxxer.hikari.HikariDataSource
import slick.driver.JdbcDriver
import slick.jdbc._
import slick.util.{ CloseableIterator, ReadAheadIterator }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

object SqlDatabase {

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

    def getLocalDateTime(name: String, timeZoneId: ZoneId): LocalDateTime = getLocalDateTimeOption(name, timeZoneId).get

    def getByteArrayOption(name: String): Option[Array[Byte]]

    def getByteArray(name: String): Array[Byte] = getByteArrayOption(name).get

    def withBlobOptionInputStream[A](name: String)(f: InputStream => A): Option[A]

    def withBlobInputStream[A](name: String)(f: InputStream => A): A = withBlobOptionInputStream(name)(f).get

    def withClobOptionInputStream[A](name: String)(f: InputStream => A): Option[A]

    def withClobInputStream[A](name: String)(f: InputStream => A): A = withClobOptionInputStream(name)(f).get

  }

  object Implicits {

    implicit def sqlInterpolation(s: StringContext): SqlInterpolation = driver.api.actionBasedSQLInterpolation(s)

    implicit class SqlOps(builder: Sql) {

      def stripMargin(marginChar: Char): Sql =
        builder.copy(builder.queryParts.map(_.asInstanceOf[String].stripMargin(marginChar)))

      def stripMargin: Sql = builder.copy(builder.queryParts.map(_.asInstanceOf[String].stripMargin))

    }

    implicit class ResultSetOps(rs: ResultSet) extends Row {

      override def getBooleanOption(name: String): Option[Boolean] = Option(rs.getBoolean(name))

      override def getByteOption(name: String): Option[Byte] = Option(rs.getByte(name))

      override def getShortOption(name: String): Option[Short] = Option(rs.getShort(name))

      override def getIntOption(name: String): Option[Int] = Option(rs.getInt(name))

      override def getLongOption(name: String): Option[Long] = Option(rs.getLong(name))

      override def getFloatOption(name: String): Option[Float] = Option(rs.getFloat(name))

      override def getDoubleOption(name: String): Option[Double] = Option(rs.getDouble(name))

      override def getBigDecimalOption(name: String): Option[BigDecimal] = Option(rs.getBigDecimal(name))

      override def getStringOption(name: String): Option[String] = Option(rs.getString(name))

      override def getLocalDateOption(name: String): Option[LocalDate] = Option(rs.getDate(name)).map(_.toLocalDate)

      override def getLocalTimeOption(name: String): Option[LocalTime] = Option(rs.getTime(name)).map(_.toLocalTime)

      override def getLocalDateTimeOption(name: String): Option[LocalDateTime] =
        getLocalDateTimeOption(name, ZoneId.systemDefault())

      override def getLocalDateTimeOption(name: String, timeZoneId: ZoneId): Option[LocalDateTime] =
        Option(rs.getTimestamp(name)).map { ts =>
          LocalDateTime.ofInstant(ts.toInstant, timeZoneId)
        }

      override def getByteArrayOption(name: String): Option[Array[Byte]] = Option(rs.getBytes(name))

      override def withBlobOptionInputStream[A](name: String)(f: InputStream => A): Option[A] =
        Option(rs.getBlob(name)).map { b =>
          arm.using(b.getBinaryStream) { f }
        }

      override def withClobOptionInputStream[A](name: String)(f: InputStream => A): Option[A] =
        Option(rs.getClob(name)).map { b =>
          arm.using(b.getAsciiStream) { f }
        }
    }
  }

  private[resource] val DefaultConfigPath = "sql-database"

  private[resource] val defaultReference = ConfigFactory.defaultReference.getConfig(DefaultConfigPath)

  private[resource] val driver = new JdbcDriver {}

  private[resource] class ResultSetIterator[+A](rs: ResultSet, maxRows: Int, autoClose: Boolean)(f: Row => A)
      extends ReadAheadIterator[A]
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

    final def close() {
      if (!closed) {
        rs.close()
        closed = true
      }
    }
  }

  private[resource] def setParameters(ps: PreparedStatement, params: Seq[Option[Any]]): Unit = {
    params.zipWithIndex foreach {
      case (None, index)        => ps.setNull(index + 1, Types.OTHER)
      case (Some(param), index) => ps.setObject(index + 1, param)
    }
  }

  private[resource] def withTimeout[A](ps: PreparedStatement, timeout: Timeout)(f: PreparedStatement => A)(
      implicit ec: ExecutionContext): A = {
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
    val result = try {
      f(ps)
    } catch {
      case ex: SQLException => if (canceller.cancelled) throw new SQLTimeoutException() else throw ex
    } finally {
      canceller.finished = true
      timedOut.countDown()
      //ps.synchronized(ps.notifyAll())
      cancellerDone.await()
    }
    if (canceller.cancelled) throw new SQLTimeoutException()
    result
  }

  private[resource] def withPreparedStatement[A](conn: java.sql.Connection, sql: String)(f: PreparedStatement => A): A =
    arm.using(conn.prepareStatement(sql)) { ps =>
      //ps.setQueryTimeout(timeout.toSeconds)
      f(ps)
    }

  private[resource] def createDataSource(config: Config): HikariDataSource = {
    // This verifies that the Config is sane and has our
    // reference config. Importantly, we specify the "sql-database"
    // path so we only validate settings that belong to this
    // library. Otherwise, we might throw mistaken errors about
    // settings we know nothing about.
    config.checkValid(ConfigFactory.defaultReference().getConfig("sql-database"))

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
    ds.setInitializationFailFast(config.getBoolean("initialization-fail-fast"))
    ds.setIsolateInternalQueries(config.getBoolean("isolate-internal-queries"))
    ds.setAllowPoolSuspension(config.getBoolean("allow-pool-suspension"))
    ds.setReadOnly(config.getBoolean("read-only"))
    if (config.getString("catalog") != "") ds.setCatalog(config.getString("catalog"))
    if (config.getString("connection-init-sql") != "") ds.setConnectionInitSql(config.getString("connection-init-sql"))
    if (config.getString("driver-class-name") != "") ds.setDriverClassName(config.getString("driver-class-name"))
    if (config.getString("transaction-isolation") != "")
      ds.setTransactionIsolation(config.getString("transaction-isolation"))
    ds.setValidationTimeout(config.getDuration("validation-timeout", TimeUnit.MILLISECONDS))
    ds.setLeakDetectionThreshold(config.getDuration("leak-detection-threshold", TimeUnit.MILLISECONDS))

    val props: Properties = new Properties()
    config.getConfig("properties").entrySet().asScala.foreach { a =>
      props.put(a.getKey, a.getValue.render())
    }
    ds.setDataSourceProperties(props)
    // TODO
    //ds.setScheduledExecutorService()

    ds
  }

  /*private[resource] val uriSchemeToDriverClassName = Map (
  "h2" -> "org.h2.Driver",
  "oracle" -> "oracle.jdbc.OracleDriver",
  "postgres" -> "org.postgresql.Driver",
  "pgsql" -> "com.impossibl.postgres.jdbc.PGDriver",
  "mysql" -> "com.mysql.jdbc.Driver",
  "mariadb" -> "org.mariadb.jdbc.Driver",
  "sqlserver" -> "com.microsoft.sqlserver.Driver",
  "impala" -> "com.cloudera.impala.jdbc4.Driver",
  "hive2" -> "org.apache.hive.jdbc.HiveDriver"
)*/

  /*private[resource] val uriSchemeToDataSourceClassName = Map (
    "h2" -> "org.h2.jdbcx.JdbcDataSource",
    "oracle" -> "oracle.jdbc.pool.OracleDataSource",
    "pgsql" -> "com.impossibl.postgres.jdbc.PGDataSource",
    "mysql" -> "org.mariadb.jdbc.MySQLDataSource",
    "mariadb" -> "org.mariadb.jdbc.MySQLDataSource",
    "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDataSource",
    "impala" -> "com.cloudera.impala.jdbc4.ImpalaJDBC4DataSource"
    )*/

  /*private[resource] def sqlType(obj: Any): Int = obj match {
    case v: Boolean => Types.BOOLEAN
    case v: BigDecimal => Types.BIGINT
    case v: Blob => Types.BLOB
    case v: Byte => Types.TINYINT
    case v: Clob => Types.CLOB
    case v: Date => Types.DATE
    case v: Double => Types.DOUBLE
    case v: Float => Types.REAL
    case v: Int => Types.INTEGER
    case v: Long => Types.BIGINT
    case v: Short => Types.SMALLINT
    case v: String => Types.VARCHAR
    case v: Time => Types.TIME
    case v: Timestamp => Types.TIMESTAMP
    case v => throw new SQLException("Parameter type not supported for " + v)
  }*/

}
