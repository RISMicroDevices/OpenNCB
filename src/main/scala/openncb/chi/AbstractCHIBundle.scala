package cn.rismd.openncb.chi

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters

abstract class AbstractCHIBundle(implicit val p: Parameters) extends Bundle {
    
    val paramCHI = p(CHIParametersKey)
}
