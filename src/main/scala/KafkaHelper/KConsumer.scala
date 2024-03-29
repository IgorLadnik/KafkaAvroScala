package KafkaHelper

import java.util._
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class KConsumer(private val config: Properties,
                private val p: (String, GenericRecord, Long) => Unit,
                private val logger: (String) => Unit) {

  @volatile private var continue = true
  @volatile private var isEnded = false

  // Ctor

  private val topic = config.get(KafkaPropNames.Topic).asInstanceOf[String]
  private val schemaRegistryUrl = config.get(KafkaPropNames.SchemaRegistryUrl).asInstanceOf[String]

  private val recordConfig = new RecordConfig(config.get(KafkaPropNames.SchemaRegistryUrl).asInstanceOf[String])
  private val schemaRegistryClient = recordConfig.getSchemaRegistryClient(schemaRegistryUrl)
  private val kafkaAvroDeserializer = new KafkaAvroDeserializer(schemaRegistryClient)

  config.put(KafkaPropNames.KeyDeserializer, classOf[StringDeserializer].getCanonicalName)
  config.put(KafkaPropNames.ValueDeserializer, classOf[ByteArrayDeserializer].getCanonicalName)

  // Use Specific Record or else you get Avro GenericRecord.
  config.put("specific.avro.reader", "false")

  private val consumer = new KafkaConsumer[String, Array[Byte]](config)

  // Methods

  def startConsuming: KConsumer = {
    startConsumingInner.onComplete {
      case Success(u: Unit) => isEnded = true
      case Failure(e: Exception) => { consumer.close; logger(e.getMessage)  }
    }
    this
  }

  private def startConsumingInner: Future[Unit] = Future {
    consumer.subscribe(Arrays.asList(topic))
    while (continue) {
      val record = consumer.poll(100).asScala
      for (data <- record.iterator) {
        try p(data.key, deserialize(data.value, topic), data.timestamp)
        catch { case e: Exception => logger(e.getMessage) }
      }
    }

    consumer.unsubscribe
    consumer.close;
  }

  def deserialize(bts: Array[Byte], topic: String): GenericRecord =
    kafkaAvroDeserializer.deserialize(topic, bts, recordConfig.schema).asInstanceOf[GenericRecord]

  def close = {
    continue = false
    while (!isEnded) { }
    logger("Kafka Consumer closed");
  }
}
