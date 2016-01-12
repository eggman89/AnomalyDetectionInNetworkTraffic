package eggman89

import org.apache.log4j.{Level, Logger}
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkContext, SparkConf}

/**
  * Created by sneha on 1/11/2016.
  */
object writeSample {

  def main(args: Array[String]) {

    /*spark stuff*/
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)
    Logger.getLogger("INFO").setLevel(Level.OFF)
    System.setProperty("hadoop.home.dir", "c:/winutil/")
    val conf = new SparkConf().setAppName("AnamolyDetection").set("spark.serializer", "org.apache.spark.serializer.KryoSerializer").set("spark.executor.memory", "4g").set("spark.default.parallelism", "3").setMaster("local[*]")
    val sc = new SparkContext(conf)

    /*setting up sql context to query the data later on*/
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._
    println("Spark Context started")
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)
    Logger.getLogger("INFO").setLevel(Level.OFF)

    //load data
    val rawData = sc.textFile("C:/Users/sneha/Google Drive/Github/DataSet/Anomaly-Detection-In-Network-Traffic/kddcup.data.corrected")

    //remove text enteries to conver it to a vector
    val labelsAndData = rawData.map { line =>
      val buffer = line.split(',').toBuffer //toBuffer creates Buffer, a mutable list
      buffer.remove(1, 3)  //remove first three elements
    val label = buffer.remove(buffer.length-1) //remove last elemnets
    val vector = Vectors.dense(buffer.map(_.toDouble).toArray)
      (label,vector)
    }
    val data = labelsAndData.values
    val kmeans = new KMeans()
    val model = kmeans.setK(100).run(data)


    val sample = data.map(datum =>
      model.predict(datum) + "," + datum.toArray.mkString(",")
    ).sample(false, 0.20)
    sample.saveAsTextFile("sample")

  }


  }
