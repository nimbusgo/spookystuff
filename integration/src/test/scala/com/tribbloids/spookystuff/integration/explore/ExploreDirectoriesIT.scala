package com.tribbloids.spookystuff.integration.explore

import com.tribbloids.spookystuff.actions.Wget
import com.tribbloids.spookystuff.integration.IntegrationFixture
import com.tribbloids.spookystuff.testutils.TestJARResolver

/**
  * Created by peng on 29/01/17.
  */
class ExploreDirectoriesIT extends IntegrationFixture {

  import com.tribbloids.spookystuff.dsl._

  override lazy val driverFactories = Seq(
    null
  )

  val _resourcePath = TestJARResolver.unpackedURL("testutils/dir").getPath
  lazy val resourcePath = _resourcePath

  override def doMain(): Unit = {
    val url = resourcePath
    val rootDoc = spooky.create(Seq(url))
      .fetch{
        Wget('_)
      }
    val nodes = rootDoc
      .explore(S"root directory Path".texts)(
        Wget('A)
      )()
      .extract(S.uri ~ 'uri)
    val result = nodes
      .flatExtract(S"root file")(
        A"Name".text ~ 'leaf,
        A"Path".text ~ 'fullPath
      )
      .toDF(sort = true)

    result.schema.treeString.shouldBe(
      """
        |root
        | |-- _: string (nullable = true)
        | |-- uri: string (nullable = true)
        | |-- leaf: string (nullable = true)
        | |-- fullPath: string (nullable = true)
      """.stripMargin
    )

    val formatted = result.toJSON.collect().mkString("\n")
    formatted.shouldBe (
      s"""
         |{"_":"$resourcePath","uri":"file:${_resourcePath}","leaf":"table.csv","fullPath":"file:${_resourcePath}/table.csv"}
         |{"_":"$resourcePath","uri":"file:${_resourcePath}","leaf":"hivetable.csv","fullPath":"file:${_resourcePath}/hivetable.csv"}
         |{"_":"$resourcePath","uri":"file:${_resourcePath}/dir","leaf":"Test.pdf","fullPath":"file:${_resourcePath}/dir/Test.pdf"}
         |{"_":"$resourcePath","uri":"file:${_resourcePath}/dir/dir","leaf":"pom.xml","fullPath":"file:${_resourcePath}/dir/dir/pom.xml"}
         |{"_":"$resourcePath","uri":"file:${_resourcePath}/dir/dir/dir","leaf":"tribbloid.json","fullPath":"file:${_resourcePath}/dir/dir/dir/tribbloid.json"}
      """.stripMargin,
      sort = true
    )
  }

  override def numPages = 4
  override def pageFetchedCap = 16 // way too large
}
