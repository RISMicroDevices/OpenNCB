package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi._

abstract class AbstractAXI4Channel[+T <: Data](gen: T) extends IrrevocableIO[T](gen)
