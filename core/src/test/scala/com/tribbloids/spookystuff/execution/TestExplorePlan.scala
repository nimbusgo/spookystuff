package com.tribbloids.spookystuff.execution

import com.tribbloids.spookystuff.actions.Wget
import com.tribbloids.spookystuff.{SpookyEnvSuite, dsl}
import org.apache.spark.HashPartitioner

/**
  * Created by peng on 05/04/16.
  */
class TestExplorePlan extends SpookyEnvSuite {

  import dsl._

  test("ExplorePlan.toString should work") {

    val base = spooky
      .fetch(
        Wget("http://webscraper.io/test-sites/e-commerce/allinone")
      )

    val explored = base
      .explore(S"div.sidebar-nav a", ordinalField = 'index)(
        Wget('A.href),
        depthField = 'depth
      )(
        'A.text ~ 'category,
        S"h1".text ~ 'header
      )

    println(explored.plan.toString)
  }

  test("ExplorePlan should create a new beaconRDD if its upstream doesn't have one"){
    val partitioner = new HashPartitioner(8)

    val src = spooky
      .extract("abc" ~ 'dummy)

    assert(src.plan.localityBeaconRDDOpt.isEmpty)

    val rdd1 = src
      .explore('dummy)(
        Wget(STATIC_WIKIPEDIA_URI),
        fetchOptimizer = FetchOptimizers.WebCacheAware,
        partitionerFactory = {v => partitioner}
      )()

    assert(rdd1.plan.localityBeaconRDDOpt.get.partitioner.get eq partitioner)
  }


  test("ExplorePlan should inherit old beaconRDD from upstream if exists") {
    val partitioner = new HashPartitioner(8)
    val partitioner2 = new HashPartitioner(16)

    val rdd1 = spooky
      .extract("abc" ~ 'dummy)
      .explore('dummy)(
        Wget(STATIC_WIKIPEDIA_URI),
        fetchOptimizer = FetchOptimizers.WebCacheAware,
        partitionerFactory = {v => partitioner}
      )()

    assert(rdd1.plan.localityBeaconRDDOpt.get.partitioner.get eq partitioner)
    val beaconRDD = rdd1.plan.localityBeaconRDDOpt.get

    val rdd2 = rdd1
      .explore('dummy)(
        Wget(STATIC_WIKIPEDIA_URI),
        fetchOptimizer = FetchOptimizers.WebCacheAware,
        partitionerFactory = {v => partitioner2}
      )()

    assert(rdd2.plan.localityBeaconRDDOpt.get.partitioner.get eq partitioner)
    assert(rdd2.plan.localityBeaconRDDOpt.get eq beaconRDD)
  }
}
