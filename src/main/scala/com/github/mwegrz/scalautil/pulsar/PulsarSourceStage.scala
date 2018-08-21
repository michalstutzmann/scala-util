//package com.github.mwegrz.scalautil.pulsar
//
//import akka.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, OutHandler }
//import akka.stream.{ Attributes, Outlet, SourceShape }
//import com.sksamuel.pulsar4s.akka.streams.Control
//import com.sksamuel.pulsar4s.Reader
//
//import scala.concurrent.ExecutionContext
//import scala.util.{ Failure, Success }
//
//class PulsarSourceStage(create: () => Reader)
//    extends GraphStageWithMaterializedValue[SourceShape[com.sksamuel.pulsar4s.Message], Control] {
//
//  private val out = Outlet[com.sksamuel.pulsar4s.Message]("pulsar.out")
//  override def shape: SourceShape[com.sksamuel.pulsar4s.Message] = SourceShape(out)
//
//  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Control) = {
//    val logic = new GraphStageLogic(shape) with OutHandler {
//      setHandler(out, this)
//
//      implicit val context: ExecutionContext = super.materializer.executionContext
//      val reader = create()
//
//      override def onPull(): Unit = {
//        reader.nextAsync.onComplete {
//          case Success(msg) => push(out, msg)
//          case Failure(t)   => failStage(t)
//        }
//      }
//
//      override def postStop(): Unit = reader.closeAsync
//    }
//
//    val control = new Control {
//      override def close(): Unit = {
//        logic.completeStage()
//      }
//    }
//
//    (logic, control)
//  }
//}
