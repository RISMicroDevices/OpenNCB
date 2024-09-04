package cn.rismd.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.axi._

abstract class AbstractAXI4Channel[+T <: Data](gen: T) extends DecoupledIO[T](gen)
