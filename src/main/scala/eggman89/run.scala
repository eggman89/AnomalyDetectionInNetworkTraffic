package eggman89

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.{Vectors, Vector}
import org.apache.spark.rdd.RDD

object run {

  def buildNormalizationFunction(data: RDD[Vector]): (Vector => Vector) = {
    val dataAsArray = data.map(_.toArray)
    val numCols = dataAsArray.first().length
    val n = dataAsArray.count()
    val sums = dataAsArray.reduce(
      (a, b) => a.zip(b).map(t => t._1 + t._2))
    val sumSquares = dataAsArray.fold(
      new Array[Double](numCols)
    )(
      (a, b) => a.zip(b).map(t => t._1 + t._2 * t._2)
    )
    val stdevs = sumSquares.zip(sums).map {
      case (sumSq, sum) => math.sqrt(n * sumSq - sum * sum) / n
    }
    val means = sums.map(_ / n)

    (datum: Vector) => {
      val normalizedArray = (datum.toArray, means, stdevs).zipped.map(
        (value, mean, stdev) =>
          if (stdev <= 0) (value - mean) else (value - mean) / stdev
      )
      Vectors.dense(normalizedArray)
    }
  }
  def entropy(counts: Iterable[Int]) = {
    val values = counts.filter(_ > 0)
    val n: Double = values.sum
    values.map { v =>
      val p = v / n
      -p * math.log(p)
    }.sum
  }

  def normclusteringScore(normalizedLabelsAndData: RDD[(String, Vector)], k: Int) = {

    val kmeans = new KMeans()
    val model = kmeans.run(normalizedLabelsAndData.values)
    val labelsAndClusters =
      normalizedLabelsAndData.mapValues(model.predict)
    val clustersAndLabels = labelsAndClusters.map(_.swap)
    val labelsInCluster = clustersAndLabels.groupByKey().values
    val labelCounts = labelsInCluster.map(
      _.groupBy(l => l).map(_._2.size))

    val n = normalizedLabelsAndData.count()
    labelCounts.map(m => m.sum * entropy(m)).sum / n
  }




  def clusteringScore(data: RDD[Vector], k: Int) = {

    val kmeans = new KMeans()
    kmeans.setK(k)
    val model = kmeans.run(data)

    val temp = data.map(datum => distance.distToCentroid(datum, model))
    temp.mean()
    // println("here3",temp.mean())
  }


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
    // val rawData = sc.textFile("C:/Users/sneha/Google Drive/Github/DataSet/Anomaly-Detection-In-Network-Traffic/kddcup.data.corrected")

    val rawData = sc.parallelize(sc.textFile("C:/Users/sneha/Google Drive/Github/DataSet/Anomaly-Detection-In-Network-Traffic/kddcup.data.corrected").take(100).toSeq)

    println("Set number of K (10-300 ) in a multiple of 10 [Enter 150 for default]")
    val numK = readInt()
    println("Normalized : 0(False) or 1(True)")
    val boolNorm = readInt()
    //exploring data
    println("total rows:", rawData.count())
    //rawData.map(r => r.split(',').mkString(" ")).foreach(println)

    /*rawData.map(_.split(',').last).countByValue().toSeq.
      sortBy(_._2).reverse.foreach(println)*/


    //transforming data

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
    var model = kmeans.setK(numK).run(data)

    if (boolNorm  == 0  ) {

      //build K-Means

      //model.clusterCenters.foreach(println)

      //making sense of clusters (before normalizing)
      // cluster : number , label , number of objects
      val clusterLabelCount = labelsAndData.map { case (label, datum) =>
        val cluster = model.predict(datum)
        (cluster, label)
      }.countByValue
      clusterLabelCount.toSeq.sorted.foreach {
        case ((cluster, label), count) =>
          println(f"$cluster%1s$label%18s$count%8s")
      }

      (10 to numK by 10).map(k => (k, clusteringScore(data, k))).foreach(println)
    }
    else {
      //normalizing score
      val dataAsArray = data.map(_.toArray)
      val numCols = dataAsArray.first().length
      val n = dataAsArray.count()
      val sums = dataAsArray.reduce(
        (a, b) => a.zip(b).map(t => t._1 + t._2))

      val sumSquares = dataAsArray.fold(
        new Array[Double](numCols))((a, b) => a.zip(b).map(t => t._1 + t._2 * t._2))

      val stdevs = sumSquares.zip(sums).map { case (sumSq, sum) => math.sqrt(n * sumSq - sum * sum) / n }

      val means = sums.map(_ / n)

      def normalize(datum: Vector) = {
        val normalizedArray = (datum.toArray, means, stdevs).zipped.map(
          (value, mean, stdev) =>
            if (stdev <= 0) (value - mean) else (value - mean) / stdev
        )
        Vectors.dense(normalizedArray)
      }



      val normalizedData = data.map(normalize).cache()
      //making sense of clusters (after normalizing)
      val normalizedLabelsAndData =
        labelsAndData.mapValues(buildNormalizationFunction(labelsAndData.values)).cache()

      (10 to numK by 10).par.map(k =>
        (k, normclusteringScore(normalizedLabelsAndData, k))).toList.foreach(println)


      // Detect anomalies
      def buildCategoricalAndLabelFunction(rawData: RDD[String]): (String => (String,Vector)) = {
        val splitData = rawData.map(_.split(','))
        val protocols = splitData.map(_(1)).distinct().collect().zipWithIndex.toMap
        val services = splitData.map(_(2)).distinct().collect().zipWithIndex.toMap
        val tcpStates = splitData.map(_(3)).distinct().collect().zipWithIndex.toMap
        (line: String) => {
          val buffer = line.split(',').toBuffer
          val protocol = buffer.remove(1)
          val service = buffer.remove(1)
          val tcpState = buffer.remove(1)
          val label = buffer.remove(buffer.length - 1)
          val vector = buffer.map(_.toDouble)

          val newProtocolFeatures = new Array[Double](protocols.size)
          newProtocolFeatures(protocols(protocol)) = 1.0
          val newServiceFeatures = new Array[Double](services.size)
          newServiceFeatures(services(service)) = 1.0
          val newTcpStateFeatures = new Array[Double](tcpStates.size)
          newTcpStateFeatures(tcpStates(tcpState)) = 1.0

          vector.insertAll(1, newTcpStateFeatures)
          vector.insertAll(1, newServiceFeatures)
          vector.insertAll(1, newProtocolFeatures)

          (label, Vectors.dense(vector.toArray))
        }
      }

      def buildAnomalyDetector(
                                data: RDD[Vector],
                                normalizeFunction: (Vector => Vector)): (Vector => Boolean) = {
        val normalizedData = data.map(normalizeFunction)
        normalizedData.cache()

        val kmeans = new KMeans()
        kmeans.setK(150)
        kmeans.setRuns(10)
        kmeans.setEpsilon(1.0e-6)
        val model = kmeans.run(normalizedData)

        normalizedData.unpersist()

        val distances = normalizedData.map(datum => distance.distToCentroid(datum, model))
        val threshold = distances.top(100).last

        (datum: Vector) => distance.distToCentroid(normalizeFunction(datum), model) > threshold
      }

      def anomalies(rawData: RDD[String]) = {
        val parseFunction = buildCategoricalAndLabelFunction(rawData)
        val originalAndData = rawData.map(line => (line, parseFunction(line)._2))
        val data = originalAndData.values
        val normalizeFunction = buildNormalizationFunction(data)
        val anomalyDetector = buildAnomalyDetector(data, normalizeFunction)
        val anomalies = originalAndData.filter {
          case (original, datum) => anomalyDetector(datum)
        }.keys
        anomalies.take(10).foreach(println)
      }


    }




  }

}

