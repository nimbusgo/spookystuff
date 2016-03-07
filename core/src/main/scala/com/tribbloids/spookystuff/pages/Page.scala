package com.tribbloids.spookystuff.pages

import java.io._
import java.util.{Date, UUID}

import com.tribbloids.spookystuff._
import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.utils.{IdentifierMixin, Utils}
import org.apache.commons.csv.CSVFormat
import org.apache.hadoop.fs.Path
import org.apache.http.StatusLine
import org.apache.http.entity.ContentType
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{Metadata, TikaMetadataKeys}
import org.apache.tika.mime.MimeTypes
import org.mozilla.universalchardet.UniversalDetector

/**
  * Created by peng on 04/06/14.
  */
//use to genterate a lookup key for each page so
@SerialVersionUID(612503421395L)
case class PageUID(
                    backtrace: Trace,
                    output: Export,
                    //                    sessionStartTime: Long,
                    blockIndex: Int = 0,
                    blockSize: Int = 1 //number of pages in a block output,
                  ) {

}

trait PageLike {
  def uid: PageUID
  def cacheable: Boolean

  def name = Option(this.uid.output).map(_.name).orNull
}

//case class Unfetched(
//                      trace: Trace
//                    ) extends PageLike {
//
//  def cacheable: Boolean = false
//
//  @transient override lazy val uid: PageUID = PageUID(trace, null, 0, 1)
//}

trait Fetched extends PageLike {
  def timestamp: Date

  def laterThan(v2: Fetched): Boolean = this.timestamp after v2.timestamp

  def laterOf(v2: Fetched): Fetched = if (laterThan(v2)) this
  else v2

//  def revertToUnfetched: Unfetched = Unfetched(uid.backtrace)
}

//Merely a placeholder when a Block returns nothing
case class NoPage(
                   trace: Trace,
                   override val timestamp: Date = new Date(System.currentTimeMillis()),
                   override val cacheable: Boolean = true
                 ) extends Serializable with Fetched {

  @transient override lazy val uid: PageUID = PageUID(trace, null, 0, 1)
}

class ErrorWithPage(
                     delegate: Page,
                     override val message: String = "",
                     override val cause: Throwable = null
                   ) extends ActionException with Fetched {

  override def timestamp: Date = delegate.timestamp

  override def uid: PageUID = delegate.uid

  override def cacheable: Boolean = delegate.cacheable
}

class DocumentFilterError(
                           delegate: Page,
                           override val message: String = "",
                           override val cause: Throwable = null
                         ) extends ErrorWithPage(delegate, message, cause)

object Page {

  val CONTENT_TYPE = "contentType"
  val CSV_FORMAT = "csvFormat"

  val defaultCSVFormat = CSVFormat.DEFAULT
}

//keep small, will be passed around by Spark
@SerialVersionUID(94865098324L)
case class Page(
                 override val uid: PageUID,

                 override val uri: String, //redirected
                 declaredContentType: Option[String],
                 content: Array[Byte],

                 //                 cookie: Seq[SerializableCookie] = Nil,
                 override val timestamp: Date = new Date(System.currentTimeMillis()),
                 saved: scala.collection.mutable.Set[String] = scala.collection.mutable.Set(),
                 override val cacheable: Boolean = true,
                 httpStatus: Option[StatusLine] = None,
                 @transient _properties: Map[String, Any] = null //for customizing parsing
               )
  extends Unstructured with Fetched with IdentifierMixin {

  def properties: Map[String, Any] = Option(_properties).getOrElse(Map())

  lazy val _id = (uid, uri, declaredContentType, timestamp, httpStatus.toString)

  private def detectCharset(contentType: ContentType): String = {
    val charsetD = new UniversalDetector(null)
    val ss = 4096

    for (i <- 0.until(content.length, ss)) {
      val length = Math.min(content.length - i, ss)
      charsetD.handleData(content, i, length)
      if (charsetD.isDone) return charsetD.getDetectedCharset
    }

    charsetD.dataEnd()
    val detected = charsetD.getDetectedCharset

    if (detected == null) {
      if (contentType.getMimeType.contains("text")) Const.defaultTextCharset
      else if (contentType.getMimeType.contains("application")) Const.defaultApplicationCharset
      else Const.defaultApplicationCharset
    }
    else detected
  }

  @transient lazy val parsedContentType: ContentType = properties
    .get(Page.CONTENT_TYPE)
    .map("" + _)
    .orElse(declaredContentType) match {
    case Some(str) =>
      val ct = ContentType.parse(str)
      if (ct.getCharset == null) {

        ct.withCharset(detectCharset(ct))
      }
      else ct
    case None =>
      val metadata = new Metadata()
      val slash: Int = uri.lastIndexOf('/')
      metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, uri.substring(slash + 1))
      val stream = TikaInputStream.get(content, metadata)
      try {
        val mediaType = Const.mimeDetector.detect(stream, metadata)
        //        val mimeType = mediaType.getBaseType.toString
        //        val charset = new CharsetDetector().getString(content, null)
        //        ContentType.create(mimeType, charset)

        val str = mediaType.toString
        val result = ContentType.parse(str)
        if (result.getCharset == null) {

          result.withCharset(detectCharset(result))
        }
        else result
      }
      finally {
        stream.close()
      }
  }

  //TODO: use reflection to find any element implementation that can resolve supplied MIME type
  @transient lazy val root: Unstructured = {
    val effectiveCharset = charset.orNull

    if (mimeType.contains("html") || mimeType.contains("xml") || mimeType.contains("directory")) {
      HtmlElement(content, effectiveCharset, uri) //not serialize, parsing is faster
    }
    else if (mimeType.contains("json")) {
      JsonElement(content, effectiveCharset, uri) //not serialize, parsing is faster
    }
    else if (mimeType.contains("csv")) {
      val csvFormat = this.properties.get(Page.CSV_FORMAT).map{
        _.asInstanceOf[CSVFormat]
      }
        .getOrElse(Page.defaultCSVFormat)

      CSVElement(content, effectiveCharset, uri, csvFormat) //not serialize, parsing is faster
    }
    else {
      TikaHtmlElement(content, effectiveCharset, mimeType, uri)
    }
  }
  def charset: Option[Selector] = Option(parsedContentType.getCharset).map(_.name())
  def mimeType: String = parsedContentType.getMimeType

  def contentType = parsedContentType.toString

  def tikaMimeType = MimeTypes.getDefaultMimeTypes.forName(mimeType)
  def exts: Array[String] = tikaMimeType.getExtensions.toArray(Array[String]()).map{
    str =>
      if (str.startsWith(".")) str.splitAt(1)._2
      else str
  }
  def defaultExt: Option[String] = exts.headOption

  override def findAll(selector: String) = root.findAll(selector)
  override def findAllWithSiblings(start: String, range: Range) = root.findAllWithSiblings(start, range)
  override def children(selector: Selector): Elements[Unstructured] = root.children(selector)
  override def childrenWithSiblings(selector: Selector, range: Range): Elements[Siblings[Unstructured]] = root.childrenWithSiblings(selector, range)
  override def code: Option[String] = root.code
  override def formattedCode: Option[String] = root.formattedCode
  override def allAttr: Option[Map[String, String]] = root.allAttr
  override def attr(attr: String, noEmpty: Boolean): Option[String] = root.attr(attr, noEmpty)
  override def href: Option[String] = root.href
  override def src: Option[String] = root.src
  override def text: Option[String] = root.text
  override def ownText: Option[String] = root.ownText
  override def boilerPipe: Option[String] = root.boilerPipe
  override def breadcrumb: Option[Seq[String]] = root.breadcrumb
  //---------------------------------------------------------------------------------------------------

  //this will lose information as charset encoding will be different
  def save(
            pathParts: Seq[String],
            overwrite: Boolean = false
          )(spooky: SpookyContext): Unit = {

    val path = Utils.uriConcat(pathParts: _*)

    PageUtils.dfsWrite("save", path, spooky) {

      val fullPath = new Path(path)
      val fs = fullPath.getFileSystem(spooky.hadoopConf)
      //      if (!overwrite && fs.exists(fullPath)) fullPath = new Path(path + "-" + UUID.randomUUID())
      val fos = try {
        fs.create(fullPath, overwrite)
      }
      catch {
        case e: Throwable =>
          val altPath = new Path(path + "-" + UUID.randomUUID())
          fs.create(altPath, overwrite)
      }

      try {
        fos.write(content) //       remember that apache IOUtils is defective for DFS!
      }
      finally {
        fos.close()
      }

      saved += fullPath.toString
    }
  }

  def autoSave(
                spooky: SpookyContext,
                overwrite: Boolean = false
              ): Unit = this.save(
    spooky.conf.dirs.autoSave :: spooky.conf.autoSaveFilePath(this).toString :: Nil
  )(spooky)

  //TODO: merge into cascade retries
  def errorDump(
                 spooky: SpookyContext,
                 overwrite: Boolean = false
               ): Unit = {
    val root = this.uid.output match {
      case ss: Screenshot => spooky.conf.dirs.errorScreenshot
      case _ => spooky.conf.dirs.errorDump
    }

    this.save(
      root :: spooky.conf.errorDumpFilePath(this).toString :: Nil
    )(spooky)
  }

  def errorDumpLocally(
                        spooky: SpookyContext,
                        overwrite: Boolean = false
                      ): Unit = {
    val root = this.uid.output match {
      case ss: Screenshot => spooky.conf.dirs.errorScreenshotLocal
      case _ => spooky.conf.dirs.errorDumpLocal
    }

    this.save(
      root :: spooky.conf.errorDumpFilePath(this).toString :: Nil
    )(spooky)
  }

  def set(tuples: (String, Any)*): Page = this.copy(
    _properties = this.properties ++ Map(tuples: _*)
  )
}