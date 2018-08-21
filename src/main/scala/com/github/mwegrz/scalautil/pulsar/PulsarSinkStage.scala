//package com.github.mwegrz.scalautil.pulsar
//
//import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler }
//import akka.stream.{ Attributes, Inlet, SinkShape }
//import com.sksamuel.pulsar4s.{ Producer, Topic }
//
//import scala.concurrent.ExecutionContextExecutor
//import scala.util.{ Failure, Success }
//
//class PulsarSinkStage(create: Topic => Producer) extends GraphStage[SinkShape[(Topic, com.sksamuel.pulsar4s.Message)]] {
//
//  private val in = Inlet.create[(Topic, com.sksamuel.pulsar4s.Message)]("pulsar.in")
//  override def shape: SinkShape[(Topic, com.sksamuel.pulsar4s.Message)] = SinkShape.of(in)
//
//  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
//    new GraphStageLogic(shape) with InHandler {
//      setHandler(in, this)
//
//      implicit def context: ExecutionContextExecutor = super.materializer.executionContext
//
//      private val producers = Map.empty[Topic, Producer].withDefault(create)
//
//      override def onPush(): Unit = {
//        try {
//          val (topic, message) = grab(in)
//          producers(topic).sendAsync(message).onComplete {
//            case Success(_) => pull(in)
//            case Failure(t) => failStage(t)
//          }
//        } catch {
//          case t: Throwable => failStage(t)
//        }
//      }
//
//      override def preStart(): Unit = pull(in)
//      override def postStop(): Unit = producers.foreach(_._2.close())
//      override def onUpstreamFailure(t: Throwable): Unit = producers.foreach(_._2.close())
//    }
//  }
//}
