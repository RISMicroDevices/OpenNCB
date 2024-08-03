package cn.rismd.openncb.chi

import org.chipsalliance.cde.config.Parameters


trait WithCHIParameters {

    implicit val p: Parameters
    
    val paramCHI = p(CHIParametersKey)
}
