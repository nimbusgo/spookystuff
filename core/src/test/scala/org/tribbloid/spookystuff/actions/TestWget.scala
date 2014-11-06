package org.tribbloid.spookystuff.actions

import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.FunSuite
import org.tribbloid.spookystuff.SpookyContext
import org.tribbloid.spookystuff.factory.PageBuilder
import org.tribbloid.spookystuff.factory.driver.TorProxySetting

/**
 * Created by peng on 11/6/14.
 */
class TestWget extends FunSuite {

  lazy val conf: SparkConf = new SparkConf().setAppName("dummy").setMaster("local")
  lazy val sc: SparkContext = new SparkContext(conf)
  lazy val sql: SQLContext = new SQLContext(sc)
  lazy implicit val spooky: SpookyContext = new SpookyContext(sql)
  spooky.setRoot("file://"+System.getProperty("user.home")+"/spooky-unit/")
  spooky.autoSave = false
  spooky.autoCache = false
  spooky.autoRestore = false
  lazy val noProxyIP = {
      spooky.proxy = null

      val results = PageBuilder.resolvePlain(
        Wget("http://www.whatsmyuseragent.com/") :: Nil
      )(spooky)

      results(0).text1("h3.info")
    }

  test("use TOR socks5 proxy for http") {

    val newIP = {
      spooky.proxy = TorProxySetting()

      val results = PageBuilder.resolvePlain(
        Wget("http://www.whatsmyuseragent.com/") :: Nil
      )(spooky)

      results(0).text1("h3.info")
    }

    assert(newIP !== null)
    assert(newIP !== "")
    assert(newIP !== noProxyIP)
  }

  //TODO: find a test site for https!
  test("use TOR socks5 proxy for https") {

    val newIP = {
      spooky.proxy = TorProxySetting()

      val results = PageBuilder.resolvePlain(
        Wget("https://www.astrill.com/what-is-my-ip-address.php") :: Nil
      )(spooky)

      results(0).text1("h1")
    }

    assert(newIP !== null)
    assert(newIP !== "")
    assert(newIP !== noProxyIP)
  }

  test("revert proxy setting for http") {

    val newIP = {
      spooky.proxy = TorProxySetting()

      val results = PageBuilder.resolvePlain(
        Wget("http://www.whatsmyuseragent.com/") :: Nil
      )(spooky)

      results(0).text1("h3.info")
    }

    val noProxyIP2 = {
      spooky.proxy = null

      val results = PageBuilder.resolvePlain(
        Wget("http://www.whatsmyuseragent.com/") :: Nil
      )(spooky)

      results(0).text1("h3.info")
    }

    assert(newIP !== noProxyIP2)
  }

  test("revert proxy setting for https") {

    val newIP = {
      spooky.proxy = TorProxySetting()

      val results = PageBuilder.resolvePlain(
        Wget("https://www.astrill.com/what-is-my-ip-address.php") :: Nil
      )(spooky)

      results(0).text1("h1")
    }

    val noProxyIP2 = {
      spooky.proxy = null

      val results = PageBuilder.resolvePlain(
        Wget("https://www.astrill.com/what-is-my-ip-address.php") :: Nil
      )(spooky)

      results(0).text1("h1")
    }

    assert(newIP !== noProxyIP2)
  }
}
