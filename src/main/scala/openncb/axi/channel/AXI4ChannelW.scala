package cn.rismd.openncb.axi.channel

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.axi.bundle._


/*
* AXI W Channel. 
*/
class AXI4ChannelW[+T <: AXI4BundleW](gen: T) extends AbstractAXI4Channel[T](gen)


// Master W Channel.
object AXI4ChannelMasterW {

    def apply[T <: AXI4BundleW](gen: T) = new AXI4ChannelW(gen)

    def apply()(implicit p: Parameters) = new AXI4ChannelW(new AXI4BundleW)
}

// Slave W Channel.
object AXI4ChannelSlaveW {

    def apply[T <: AXI4BundleW](gen: T) = Flipped(new AXI4ChannelW(gen))

    def apply()(implicit p: Parameters) = Flipped(new AXI4ChannelW(new AXI4BundleW))
}
