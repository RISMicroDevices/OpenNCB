package cn.rismd.openncb.chi.channel

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi._

abstract class AbstractCHIChannel(implicit val p: Parameters) extends Bundle with WithCHIParameters
