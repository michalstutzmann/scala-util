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
import com.github.mwegrz.scalautil.ConfigOps

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._

object CassandraClient extends KeyValueLogging {
  def apply(config: Config)(implicit executionContext: ExecutionContext): CassandraClient =
    new DefaultCassandraClient(config.withReferenceDefaults("cassandra-client"))

  def withKeyspaceIfNotExists(config: Config)(
      implicit executionContext: ExecutionContext,
      actorMaterializer: ActorMaterializer
  ): Future[CassandraClient] = {
    val client = apply(config)
    val keyspace = config.getString("keyspace")
    val replicationStrategy = config.getString("replication-strategy")
    val replicationFactor = config.getInt("replication-factor")
    client
      .createKeyspaceIfNotExists(keyspace, replicationStrategy, replicationFactor)
      .map(_ => client)
  }
}

trait CassandraClient extends Shutdownable {
  def createSink[A <: AnyRef](cql: String)(
      statementBinder: (A, PreparedStatement) => BoundStatement
  ): Sink[A, Future[Done]]
  def createSource(cql: String, values: Seq[AnyRef]): Source[Row, NotUsed]

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done]

  def createKeyspaceIfNotExists(keyspace: String, `class`: String, replicationFactor: Int)(
      implicit actorMaterializer: ActorMaterializer
  ): Future[Done]

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
        new ExponentialReconnectionPolicy(
          reconnectionPolicyBaseDelay.toMillis,
          reconnectionPolicyMaxDelay.toMillis
        )
      )
      .build
  private implicit val session: Session = cluster.connect()

  log.debug("Initialized")

  override def createSink[A <: AnyRef](
      cql: String
  )(statementBinder: (A, PreparedStatement) => BoundStatement): Sink[A, Future[Done]] = {
    val preparedStatement = session.prepare(cql)
    CassandraSink[A](parallelism = 2, preparedStatement, statementBinder)
  }

  override def createSource(cql: String, values: Seq[AnyRef]): Source[Row, NotUsed] = {
    val statement = new SimpleStatement(cql, values: _*)
    CassandraSource(statement)
  }

  override def createKeyspaceIfNotExists(keyspace: String, `class`: String, replicationFactor: Int)(
      implicit actorMaterializer: ActorMaterializer
  ): Future[Done] =
    execute(
      s"""CREATE KEYSPACE IF NOT EXISTS $keyspace
      WITH REPLICATION = { 'class' : '${`class`}', 'replication_factor' : $replicationFactor };""".stripMargin
    )

  override def registerCodec[A](codec: TypeCodec[A]): Unit =
    cluster.getConfiguration.getCodecRegistry.register(codec)

  def execute(cql: String)(implicit actorMaterializer: ActorMaterializer): Future[Done] =
    Source.single(Unit).runWith(createSink(cql)((a, b) => new BoundStatement(b)))

  override def shutdown(): Unit = {
    session.close()
    cluster.close()
    log.debug("Shut down")
  }
}
