package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.bundle._


/*
* AXI AR Channel. 
*/
class AXI4ChannelAR[+T <: AXI4BundleAR](gen: T) extends AbstractAXI4Channel[T](gen)


// Master AR Channel.
object AXI4ChannelMasterAR {

    def apply[T <: AXI4BundleAR](gen: T) = new AXI4ChannelAR(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelAR(new AXI4BundleAR)
}

// Slave AR Channel.
object AXI4ChannelSlaveAR {

    def apply[T <: AXI4BundleAR](gen: T) = Flipped(new AXI4ChannelAR(gen))

    def apply()(implicit p: Parameters) = Flipped(new AXI4ChannelAR(new AXI4BundleAR))
}
