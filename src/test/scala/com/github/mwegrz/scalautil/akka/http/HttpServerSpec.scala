//package com.github.mwegrz.scalautil.akka.http
//
//import java.time.Instant
//
//import akka.http.scaladsl.testkit.ScalatestRouteTest
//import com.github.mwegrz.scalautil.scalatest.TestSpec
//import org.scalatest.{ BeforeAndAfterAll, Matchers }
//import org.scalatest.concurrent.Eventually
//import akka.http.scaladsl.server.Directives._
//import com.typesafe.config.ConfigFactory
//
//class HttpServerSpec extends TestSpec with Matchers with ScalatestRouteTest with Eventually with BeforeAndAfterAll {
//  private val httpApiA: HttpApi = (requestId: String, time: Instant) => path("a") { complete("a") }
//  private val httpApiB: HttpApi = (requestId: String, time: Instant) => path("b") { complete("b") }
//
//  private val config = ConfigFactory.parseString("""base-path = "test"""")
//
//  private val httpServer =
//    HttpServer(config, Set(httpApiA, httpApiB))
//
//  override def afterAll: Unit = httpServer.shutdown()
//
//  describe("Key-value Store route") {
//    it("should allow to retrieve entries") {
//      Given("a request")
//      When("sending the request")
//      Get(s"/test/b") ~> httpServer.route ~> check {
//        Then("Entries are retrieved")
//        println(response)
//      }
//    }
//  }
//}
