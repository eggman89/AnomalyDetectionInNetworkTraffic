package eggman89

import org.apache.spark.mllib.clustering._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD

object distance {

  def euclidean_distance(a: org.apache.spark.mllib.linalg.Vector, b: org.apache.spark.mllib.linalg.Vector) =
    math.sqrt(a.toArray.zip(b.toArray).
      map(p => p._1 - p._2).map(d => d * d).sum)

  def distToCentroid(datum: org.apache.spark.mllib.linalg.Vector, model: KMeansModel) = {
    val cluster = model.predict(datum)
    val centroid = model.clusterCenters(cluster)
    euclidean_distance(centroid, datum)
  }



}
