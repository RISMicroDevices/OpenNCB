package cn.rismd.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.axi.bundle._


/*
* AXI B Channel. 
*/
class AXI4ChannelB[+T <: AXI4BundleB](gen: T) extends AbstractAXI4Channel[T](gen)


// Master B Channel.
object AXI4ChannelMasterB {

    def apply[T <: AXI4BundleB](gen: T) = Flipped(new AXI4ChannelB(gen))

    def apply()(implicit p: Parameters) = Flipped(new AXI4ChannelB(new AXI4BundleB))
}

// Slave B Channel.
object AXI4ChannelSlaveB {

    def apply[T <: AXI4BundleB](gen: T) = new AXI4ChannelB(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelB(new AXI4BundleB)
}
