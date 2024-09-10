package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.bundle._


/*
* AXI W Channel. 
*/
class AXI4ChannelW[+T <: AXI4BundleW](gen: T) extends AbstractAXI4Channel[T](gen)

object AXI4ChannelW {

    def apply[T <: AXI4BundleW](gen: T) = new AXI4ChannelW(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelW(new AXI4BundleW)
}

// Master W Channel.
object AXI4ChannelMasterW {

    def apply[T <: AXI4BundleW](gen: T) = AXI4ChannelW(gen)

    def apply()(implicit p: Parameters) = AXI4ChannelW()
}

// Slave W Channel.
object AXI4ChannelSlaveW {

    def apply[T <: AXI4BundleW](gen: T) = Flipped(AXI4ChannelW(gen))

    def apply()(implicit p: Parameters) = Flipped(AXI4ChannelW())
}
