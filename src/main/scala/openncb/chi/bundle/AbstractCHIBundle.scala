package cn.rismd.openncb.chi.bundle

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi._

abstract class AbstractCHIBundle(implicit val p: Parameters) extends Bundle with WithCHIParameters
