package cn.rismd.openncb

import org.chipsalliance.cde.config.Parameters


trait WithNCBParameters {

    implicit val p: Parameters
    
    val paramNCB = p(NCBParametersKey)
}
