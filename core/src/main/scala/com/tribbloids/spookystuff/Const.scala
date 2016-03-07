package com.tribbloids.spookystuff

import com.tribbloids.spookystuff.row.Field
import org.apache.tika.detect.DefaultDetector
import org.json4s.DefaultFormats
import com.tribbloids.spookystuff.dsl.DocumentFilters

object Const {

  import scala.concurrent.duration._

  val maxLoop = Int.MaxValue

  val defaultTextCharset = "ISO-8859-1"
  val defaultApplicationCharset = "utf-8"

  //  val webClientOptions = new WebClientOptions
  //  webClientOptions.setUseInsecureSSL(true)

  val sessionInitializationTimeout = 40.seconds

  val localResourceLocalRetries = 3
  val remoteResourceLocalRetries = 2
  val DFSLocalRetries = 2
  val clusterRetries = 3

  val interactionDelayMax: Duration = 60.seconds
  val interactionDelayMin: Duration = 0.seconds
  val interactionBlock: Boolean = true

  val hardTerminateOverhead: Duration = 20.seconds

  val defaultInputKey = "_"
  val keyDelimiter = "'"
  val onlyPageExtractor = "S"
  val allPagesExtractor = "S_*"

  val groupIndexExtractor = "G"

  val defaultJoinField = Field("A", isWeak = true)

  val jsonFormats = DefaultFormats

  val mimeDetector = new DefaultDetector()

  val defaultDocumentFilter = DocumentFilters.MustHaveTitle
  val defaultImageFilter = DocumentFilters.Status2XX

  val exploreStageSize = 100
}