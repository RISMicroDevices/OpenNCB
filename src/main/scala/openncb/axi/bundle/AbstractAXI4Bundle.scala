package cc.xiangshan.openncb.axi.bundle

import chisel3.Bundle
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters

abstract class AbstractAXI4Bundle(implicit val p: Parameters) extends Bundle with WithAXI4Parameters
