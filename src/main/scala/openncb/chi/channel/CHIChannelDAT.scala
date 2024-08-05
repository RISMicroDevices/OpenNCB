package cn.rismd.openncb.chi.channel

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.bundle._


/*
* CHI DAT Channel.
*/
class CHIChannelDAT[+T <: CHIBundleDAT](gen: T)(implicit p: Parameters) extends CHIChannel[T](gen)


// TXDAT Channel.
object CHIChannelTXDAT {

    def apply[T <: CHIBundleDAT](gen: T)(implicit p: Parameters) = new CHIChannelDAT(gen)

    def apply()(implicit p: Parameters) = new CHIChannelDAT(new CHIBundleDAT)
}

// RXDAT Channel.
object CHIChannelRXDAT {

    def apply[T <: CHIBundleDAT](gen: T)(implicit p: Parameters) = Flipped(new CHIChannelDAT(gen))

    def apply()(implicit p: Parameters) = Flipped(new CHIChannelDAT(new CHIBundleDAT))
}
