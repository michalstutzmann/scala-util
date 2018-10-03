package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.http.scaladsl.server.{ PathMatcher, PathMatcher1 }
import com.github.mwegrz.scalautil.scalatest.TestSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.github.mwegrz.scalautil.store.InMemoryKeyValueStore
import io.circe.generic.extras.auto._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.{ marshaller, unmarshaller }
import io.circe.generic.extras.Configuration

import scala.util.Try

object RoutesSpec {
  implicit val configuration: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withDefaults

  private object EntryId {
    implicit val ordering: Ordering[EntryId] = Ordering.by(_.value)
  }

  private case class EntryId(value: String)

  private case class Entry(id: EntryId, sampleField: Int)

  private val entryId = EntryId("id")
  private val entry = Entry(entryId, 0)

  private implicit val entryIdPathMatcher: PathMatcher1[EntryId] =
    PathMatcher(""".+""".r) flatMap { a =>
      Try(EntryId(a)).toOption
    }

  private implicit val fromStringToEntryIdUnmarshaller: Unmarshaller[String, EntryId] =
    Unmarshaller.strict[String, EntryId](EntryId(_))
}

class RoutesSpec extends TestSpec with Matchers with ScalatestRouteTest with Eventually {
  import RoutesSpec._

  private implicit val store: InMemoryKeyValueStore[EntryId, Entry] =
    new InMemoryKeyValueStore(Map(entryId -> entry))

  private val route = routes.keyValueStore

  describe("Key-value Store route") {
    it("should allow to retrieve entries") {
      Given("a request")
      When("sending the request")
      Get(s"?count=1") ~> route ~> check {
        Then("Entries are retrieved")
        println(response)
      }
    }
  }
}
