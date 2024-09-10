package cc.xiangshan.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.axi.bundle._


/*
* AXI B Channel. 
*/
class AXI4ChannelB[+T <: AXI4BundleB](gen: T) extends AbstractAXI4Channel[T](gen)

object AXI4ChannelB {

    def apply[T <: AXI4BundleB](gen: T) = new AXI4ChannelB(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelB(new AXI4BundleB)
}

// Master B Channel.
object AXI4ChannelMasterB {

    def apply[T <: AXI4BundleB](gen: T) = Flipped(AXI4ChannelB(gen))

    def apply()(implicit p: Parameters) = Flipped(AXI4ChannelB())
}

// Slave B Channel.
object AXI4ChannelSlaveB {

    def apply[T <: AXI4BundleB](gen: T) = AXI4ChannelB(gen)

    def apply()(implicit p: Parameters) = AXI4ChannelB()
}
