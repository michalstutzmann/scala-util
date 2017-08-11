package com.github.mwegrz.scalautil.cassandra

import akka.stream.ActorMaterializer
import akka.{ Done, NotUsed }
import akka.stream.alpakka.cassandra.scaladsl.{ CassandraSink, CassandraSource }
import akka.stream.scaladsl.{ Sink, Source }
import com.datastax.driver.core._
import com.datastax.driver.extras.codecs.jdk8.InstantCodec
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import scala.concurrent.{ ExecutionContext, Future }

object CassandraClient {
  def apply(config: Config)(implicit executionContext: ExecutionContext): CassandraClient =
    new DefaultCassandraClient(config)
}

trait CassandraClient extends Shutdownable {
  def createSink[A <: AnyRef](cql: String)(
      statementBinder: (A, PreparedStatement) => BoundStatement): Sink[A, Future[Done]]
  def createSource(cql: String, values: Seq[AnyRef]): Source[Row, NotUsed]

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done]
}

class DefaultCassandraClient(config: Config)(implicit executionContext: ExecutionContext)
    extends CassandraClient
    with KeyValueLogging {
  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val cluster =
    Cluster.builder.addContactPoint(host).withPort(port).build
  cluster.getConfiguration.getCodecRegistry.register(InstantCodec.instance)
  private implicit val session = cluster.connect()

  log.debug("Initialized")

  override def createSink[A <: AnyRef](cql: String)(
      statementBinder: (A, PreparedStatement) => BoundStatement): Sink[A, Future[Done]] = {
    val preparedStatement = session.prepare(cql)
    CassandraSink[A](parallelism = 2, preparedStatement, statementBinder)
  }

  override def createSource(cql: String, values: Seq[AnyRef]): Source[Row, NotUsed] = {
    val statement = new SimpleStatement(cql, values: _*)
    CassandraSource(statement)
  }

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done] =
    Source.single(Unit).runWith(createSink(cql)((a, b) => new BoundStatement(b)))

  override def shutdown(): Unit = {
    session.close()
    cluster.close()
    log.debug("Shut down")
  }
}
