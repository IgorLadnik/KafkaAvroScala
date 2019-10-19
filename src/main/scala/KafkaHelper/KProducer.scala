package KafkaHelper

import java.util.Properties

import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class KProducer(private val config: Properties,
                private val logger: (String) => Unit) {
  // Ctor

  private val topic = config.get(KafkaPropNames.Topic).asInstanceOf[String]
  private val schemaRegistryUrl = config.get(KafkaPropNames.SchemaRegistryUrl).asInstanceOf[String]
  private val recordConfig = new RecordConfig(config.get(KafkaPropNames.SchemaRegistryUrl).asInstanceOf[String])

  private val schemaRegistryClient = recordConfig.getSchemaRegistryClient(schemaRegistryUrl)
  private val kafkaAvroSerializer = new KafkaAvroSerializer(schemaRegistryClient)

  config.put(KafkaPropNames.KeySerializer, classOf[StringSerializer].getCanonicalName)
  config.put(KafkaPropNames.ValueSerializer, classOf[ByteArraySerializer].getCanonicalName)

  private val producer = new KafkaProducer[String, Array[Byte]](config)

  // Methods

  // Same as send()
  def !(key: String, genericRecord: GenericRecord) = send(key, genericRecord)

  def send(key: String, genericRecord: GenericRecord) = {
    Await.ready(sendInner(key, genericRecord), 3 seconds).onComplete {
      case Success(u: Unit) =>
      case Failure(e: Exception) => { logger(e.getMessage); close }
    }
  }

  def getSchema = recordConfig.schema

  private def sendInner(key: String, genericRecord: GenericRecord): Future[Unit] = Future {
      producer.send(new ProducerRecord[String, Array[Byte]](topic,
                    config.get(KafkaPropNames.Partition).asInstanceOf[Int],
                    key, serialize(genericRecord, topic)))
  }

  def serialize(genericRecord: GenericRecord, topic: String): Array[Byte] =
    kafkaAvroSerializer.serialize(topic, genericRecord)

  def close {
    producer.flush
    producer.close
    println("Kafka Producer closed")
  }
}



