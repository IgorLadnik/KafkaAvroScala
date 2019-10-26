package Main

import java.util.{Properties, Random, Timer, TimerTask}
import KafkaHelper._
import akka.http.scaladsl.model.DateTime
import org.apache.avro.generic.GenericData
import scala.language.postfixOps

object Main {
  def main(args: Array[String]) {

    val isFromLocalFile = false //1

    val schemaFileName = "schema.json"
    var urlSchemaPrefix = "http://localhost:9999/"
    if (isFromLocalFile)
      urlSchemaPrefix = "wwwroot/"

    val config = new Properties
    config.put(KafkaPropNames.BootstrapServers, "localhost:9092")
    config.put(KafkaPropNames.SchemaRegistryUrl, s"${urlSchemaPrefix}${schemaFileName}")
    config.put(KafkaPropNames.Topic, "aa-topic")
    config.put(KafkaPropNames.GroupId, "aa-group")
    config.put(KafkaPropNames.Partition, 0)
    config.put(KafkaPropNames.Offset, 0)

    val consumer = new KConsumer(config,
        // Callback to process consumed (key -> value) item
        (key, value, timestamp) => {
          print(s"Scala  ${key}  ->  ")
          var i = -1
          val itr = value.getSchema.getFields.iterator
          while (itr.hasNext) {
            i += 1
            print(s"${itr.next.name} = ${value.get(i)}   ")
          }
          println(DateTime.apply(timestamp))
        },
        // Callback to process log message
        s => println(s))
      .startConsuming

    val producer = new KProducer(config,
                                 // Callback to process log message
                                 s => println(s))

    // Create avro generic record object &
    //   put data in that generic record and send it to Kafka
    var count = 0
    val rnd = new Random(15)

    val timerTask = new TimerTask {
      def run = {
        this.synchronized {
          for (i <- 0 until 10) {
            count += 1

            val gr = new GenericData.Record(producer.getSchema)

            gr.put("Id", -count)
            gr.put("Name", s"${config.get(KafkaPropNames.GroupId)}-${count}")
            gr.put("BatchId", (count / 10) + 1)
            gr.put("TextData", "Some text data")
            gr.put("NumericData", Math.abs(rnd.nextLong % 100000))

            producer ! (s"${count}", gr) // same as send()
          }
        }
      }
    }
    val timer = new Timer("timer")
    timer.schedule(timerTask, 0, 5000)

    println("\nPress <Enter> to continue...")
    System.in.read

    timer.cancel
    producer.close
    consumer.close
  }
}

