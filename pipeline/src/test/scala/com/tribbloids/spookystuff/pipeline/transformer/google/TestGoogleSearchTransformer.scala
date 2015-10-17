package com.tribbloids.spookystuff.pipeline.transformer.google

import com.tribbloids.spookystuff.pipeline.SpookyEnvSuite

/**
 * Created by peng on 25/09/15.
 */
class TestGoogleSearchTransformer extends SpookyEnvSuite {

  test("transformer should transform when all column names are given") {
    val spooky = this.spooky

    new GoogleSearchTransformer().test(spooky)
  }
}