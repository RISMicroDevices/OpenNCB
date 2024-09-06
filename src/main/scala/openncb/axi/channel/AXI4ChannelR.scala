package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.bundle._


/*
* AXI R Channel. 
*/
class AXI4ChannelR[+T <: AXI4BundleR](gen: T) extends AbstractAXI4Channel[T](gen)


// Master R Channel.
object AXI4ChannelMasterR {

    def apply[T <: AXI4BundleR](gen: T) = Flipped(new AXI4ChannelR(gen))

    def apply()(implicit p: Parameters) = Flipped(new AXI4ChannelR(new AXI4BundleR))
}

// Slave R Channel.
object AXI4ChannelSlaveR {

    def apply[T <: AXI4BundleR](gen: T) = new AXI4ChannelR(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelR(new AXI4BundleR)
}
