package cn.rismd.openncb.chi

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters

abstract class AbstractCHIChannel(implicit val p: Parameters) extends Bundle with WithCHIParameters
