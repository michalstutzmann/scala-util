//package akka.http.netemera
//
//import java.time.Duration
//
//import akka.NotUsed
//import akka.http.scaladsl.HttpExt
//import akka.http.scaladsl.model.Uri
//import akka.http.scaladsl.model.ws._
//import akka.stream.scaladsl.{ Flow, Source }
//import akka.http.netemera._
//import com.github.mwegrz.scalastructlog.KeyValueLogging
//import netemera.util.javaDurationToDuration
//
//import scala.concurrent.{ ExecutionContext, Future }
//import scala.concurrent.duration.FiniteDuration
//import scala.util.{ Failure, Success }
//
//class WebSocketClient(httpExt: HttpExt)(implicit executionContext: ExecutionContext) extends KeyValueLogging {
//
//  private val retryDelay: FiniteDuration = ???
//
//  def createFlow(uri: Uri): Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
//    val request = WebSocketRequest(uri = uri)
//
//    def createClientFlow() = httpExt.pingEnabledWebSocketClientFlow(request).watchTermination()((_, a) => a.onComplete {
//      case Success(a) => log.info("Flow closed", "a" -> a)
//      case Failure(e) => log.error("Flow failed", e)
//    })
//
//    createClientFlow().
//      recoverWithRetries(Int.MaxValue, {
//        case t: Throwable =>
//          log.error(s"Flow connection failed. Retrying in $retryDelay", t)
//          Source.maybe.
//            viaMat(createClientFlow().initialDelay(retryDelay))((_, _) => NotUsed)
//      }).idleTimeout(Duration.ofDays(1))
//  }
//}
