package KafkaHelper

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import org.apache.avro.Schema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.parsing.json.JSON

class RecordConfig(val schemaRegistryUrl: String) {

  // Ctor

  val strConfig = getConfigString
  val strSchema = getSchemaString(strConfig)
  val schema = new Schema.Parser().parse(strSchema)

  val jsonValue = JSON.parseFull(strConfig)

  val jsonObj = jsonValue match {
    case Some(map: Map[String, String]) => map.asInstanceOf[Map[String, String]]
    case _  => println("JSON invalid")
  }

  val mapConfig = jsonObj.asInstanceOf[Map[String, String]]
  val subject = mapConfig.getOrElse("subject", None).toString
  val version  = mapConfig.getOrElse("version", -1).asInstanceOf[Double].toInt
  val id = mapConfig.getOrElse("id", -1).asInstanceOf[Double].toInt

  // Methods

  def getSchemaRegistryClient(schemaRegistryUrl: String): SchemaRegistryClient = {
    //2 new CachedSchemaRegistryClient(schemaRegistryUrl, 128)
    new SchemaRegistryClientEx(schema, id, version) //2
  }

  private def getSchemaString(strOrg: String): String = {
    val startIndex = strOrg.indexOf("{\"type\":")
    val endIndex = strOrg.lastIndexOf("}")
    strOrg.substring(startIndex, endIndex)
  }

  private def getConfigString: String = {
    implicit val system = ActorSystem()
    //implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    var strConfig: String = ""

    try {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = schemaRegistryUrl))
      val response = Await.result(responseFuture, 1 second)
      if (response.status.intValue == 200)
        strConfig = response.entity.toString
    }
    catch {
      case e: Exception => printf(e.getMessage)
    }

    if (strConfig.nonEmpty) {
      val startIndex = strConfig.indexOf("{")
      val endIndex = strConfig.lastIndexOf("}")
      strConfig = strConfig.substring(startIndex, endIndex + 1)
    }
    else
      strConfig = Source.fromFile(schemaRegistryUrl).mkString

    Await.result(system.terminate, 3 seconds)

    strConfig.replace("\r", "").replace("\n", "").replace("\t", "").replace(" ", "")
  }
}
