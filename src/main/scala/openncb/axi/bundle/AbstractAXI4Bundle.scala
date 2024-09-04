package cn.rismd.openncb.axi.bundle

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.axi.WithAXI4Parameters

abstract class AbstractAXI4Bundle(implicit val p: Parameters) extends Bundle with WithAXI4Parameters
