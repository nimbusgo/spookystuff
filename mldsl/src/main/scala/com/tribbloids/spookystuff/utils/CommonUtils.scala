package com.tribbloids.spookystuff.utils

import java.io.{File, InputStream}
import java.net.URL

import org.apache.spark.ml.dsl.utils.FlowUtils
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future, TimeoutException}
import scala.reflect.ClassTag
import scala.util.Random

abstract class CommonUtils {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  lazy val scalaVersion: String = scala.util.Properties.versionNumberString
  lazy val scalaBinaryVersion = scalaVersion.split('.').slice(0, 2).mkString(".")

  def numDriverCores = {
    val result = Runtime.getRuntime.availableProcessors()
    assert(result > 0)
    result
  }

  def qualifiedName(separator: String)(parts: String*) = {
    parts.flatMap(v => Option(v)).reduceLeftOption(addSuffix(separator, _) + _).orNull
  }
  def addSuffix(suffix: String, part: String) = {
    if (part.endsWith(suffix)) part
    else part+suffix
  }

  def /:/(parts: String*): String = qualifiedName("/")(parts: _*)
  def :/(part: String): String = addSuffix("/", part)

  def \\\(parts: String*): String = {
    val _parts = parts.flatMap {
      v =>
        Option(v).map (
          _.replace('/',File.separatorChar)
        )
    }
    qualifiedName(File.separator)(_parts: _*)
  }
  def :\(part: String): String = {
    val _part = part.replace('/',File.separatorChar)
    addSuffix(File.separator, _part)
  }

  // TODO: remove, use object API everywhere.
  def retry = RetryFixedInterval

  def withDeadline[T](
                       n: Duration,
                       heartbeatOpt: Option[Duration] = Some(10.seconds)
                     )(
                       fn: =>T,
                       heartbeatFn: Option[Int => Unit] = None
                     ): T = {

    val breakpointStr: String = FlowUtils.stackTracesShowStr(
      FlowUtils.getBreakpointInfo()
        .slice(1, Int.MaxValue)
      //        .filterNot(_.getClassName == this.getClass.getCanonicalName)
    )
    val startTime = System.currentTimeMillis()

    var thread: Thread = null
    val future = Future {
      thread = Thread.currentThread()
      fn
    }

    val nMillis = n.toMillis
    val terminateAt = startTime + nMillis

    val effectiveHeartbeatFn: (Int) => Unit = heartbeatFn.getOrElse {
      i =>
        val remainMillis = terminateAt - System.currentTimeMillis()
        LoggerFactory.getLogger(this.getClass).info(
          s"T - ${remainMillis.toDouble/1000} second(s)" +
            "\t@ " + breakpointStr
        )
    }

    //TODO: this doesn't terminate the future upon timeout exception! need a better pattern.
    heartbeatOpt match {
      case None =>
        try {
          Await.result(future, n)
        }
        catch {
          case e: TimeoutException =>
            LoggerFactory.getLogger(this.getClass).debug("TIMEOUT!!!!")
            throw e
        }
      case Some(heartbeat) =>
        val heartbeatMillis = heartbeat.toMillis
        for (i <- 0 to (nMillis / heartbeatMillis).toInt) {
          val remainMillis = Math.max(terminateAt - System.currentTimeMillis(), 0L)
          effectiveHeartbeatFn(i)
          val epochMillis = Math.min(heartbeatMillis, remainMillis)
          try {
            val result =  Await.result(future, epochMillis.milliseconds)
            return result
          }
          catch {
            case e: TimeoutException =>
              if (heartbeatMillis >= remainMillis) {
                Option(thread).foreach(_.interrupt())
                LoggerFactory.getLogger(this.getClass).debug("TIMEOUT!!!!")
                throw e
              }
          }
        }
        throw new UnknownError("IMPOSSIBLE")
    }
  }

  def getCPResource(str: String): Option[URL] =
    Option(ClassLoader.getSystemClassLoader.getResource(str.stripSuffix(File.separator)))
  def getCPResourceAsStream(str: String): Option[InputStream] =
    Option(ClassLoader.getSystemClassLoader.getResourceAsStream(str.stripSuffix(File.separator)))

  @scala.annotation.tailrec
  final def unboxException[T <: Throwable: ClassTag](e: Throwable): Throwable = {
    e match {
      case ee: T =>
        unboxException[T](ee.getCause)
      case _ =>
        e
    }
  }

  def timer[T](fn: => T): (T, Long) = {
    val startTime = System.currentTimeMillis()
    val result = fn
    val endTime = System.currentTimeMillis()
    (result, endTime - startTime)
  }

  def randomSuffix = Math.abs(Random.nextLong())

  def randomChars: String = {
    val len = Random.nextInt(128)
    Random.nextString(len)
  }

}

object CommonUtils extends CommonUtils
