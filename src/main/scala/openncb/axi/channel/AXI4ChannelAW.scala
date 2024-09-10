package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.bundle._


/*
* AXI AW Channel. 
*/
class AXI4ChannelAW[+T <: AXI4BundleAW](gen: T) extends AbstractAXI4Channel[T](gen)

object AXI4ChannelAW {

    def apply[T <: AXI4BundleAW](gen: T) = new AXI4ChannelAW(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelAW(new AXI4BundleAW)
}

// Master AW Channel.
object AXI4ChannelMasterAW {

    def apply[T <: AXI4BundleAW](gen: T) = AXI4ChannelAW(gen)

    def apply()(implicit p: Parameters) = AXI4ChannelAW()
}

// Slave AW Channel.
object AXI4ChannelSlaveAW {

    def apply[T <: AXI4BundleAW](gen: T) = Flipped(AXI4ChannelAW(gen))

    def apply()(implicit p: Parameters) = Flipped(AXI4ChannelAW())
}
