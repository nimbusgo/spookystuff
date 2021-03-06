package com.tribbloids.spookystuff.uav.telemetry.mavlink

import com.tribbloids.spookystuff.uav.UAVConf
import com.tribbloids.spookystuff.session.ConflictDetection
import com.tribbloids.spookystuff.session.python._

/**
  * MAVProxy: https://github.com/ArduPilot/MAVProxy
  * outlives any python driver
  * not to be confused with dsl.WebProxy
  * CAUTION: each MAVProxy instance contains 2 python processes, keep that in mind when debugging
  */
//TODO: MAVProxy supports multiple master for multiple telemetry backup
case class Proxy(
                  driverTemplate: PythonDriver,
                  master: String,
                  outs: Seq[String], //first member is always used by DK.
                  baudRate: Int,
                  ssid: Int = UAVConf.PROXY_SSID,
                  name: String
                ) extends CaseInstanceRef with BindedRef with ConflictDetection {

  assert(!outs.contains(master))
  override lazy val _resourceIDs = Map(
    "master" -> Set(master),
    "firstOut" -> outs.headOption.toSet //need at least 1 out for executor
  )

//  override protected def cleanImpl(): Unit = {
//    super.cleanImpl()
////    Proxy.existing -= this
//  }
}