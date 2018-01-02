package com.github.mwegrz.scalautil.cassandra

import akka.stream.ActorMaterializer
import akka.{ Done, NotUsed }
import akka.stream.alpakka.cassandra.scaladsl.{ CassandraSink, CassandraSource }
import akka.stream.scaladsl.{ Sink, Source }
import com.datastax.driver.core._
import com.datastax.driver.core.policies.ExponentialReconnectionPolicy
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._

object CassandraClient {
  def apply(config: Config)(implicit executionContext: ExecutionContext): CassandraClient =
    new DefaultCassandraClient(config)
}

trait CassandraClient extends Shutdownable {
  def createSink[A <: AnyRef](cql: String)(
      statementBinder: (A, PreparedStatement) => BoundStatement): Sink[A, Future[Done]]
  def createSource(cql: String, values: Seq[AnyRef]): Source[Row, NotUsed]

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done]

  def registerCodec[A](codec: TypeCodec[A]): Unit
}

class DefaultCassandraClient(config: Config)(implicit executionContext: ExecutionContext)
    extends CassandraClient
    with KeyValueLogging {
  private val contactPoints = config.getStringList("contact-points").asScala.toList
  private val port = config.getInt("port")
  private val reconnectionPolicyBaseDelay = config.getDuration("reconnection-policy.base-delay")
  private val reconnectionPolicyMaxDelay = config.getDuration("reconnection-policy.max-delay")

  private val cluster =
    Cluster.builder
      .addContactPoints(contactPoints: _*)
      .withPort(port)
      .withReconnectionPolicy(
        new ExponentialReconnectionPolicy(reconnectionPolicyBaseDelay.toMillis, reconnectionPolicyMaxDelay.toMillis))
      .build
  private implicit val session: Session = cluster.connect()

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

  override def registerCodec[A](codec: TypeCodec[A]): Unit = cluster.getConfiguration.getCodecRegistry.register(codec)

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done] =
    Source.single(Unit).runWith(createSink(cql)((a, b) => new BoundStatement(b)))

  override def shutdown(): Unit = {
    session.close()
    cluster.close()
    log.debug("Shut down")
  }
}
