package cn.rismd.openncb.chi.channel

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.bundle._


/*
* CHI RSP Channel.
*/
class CHIChannelRSP[+T <: CHIBundleRSP](gen: T)(implicit p: Parameters) extends CHIChannel[T](gen)


// TXRSP Channel.
object CHIChannelTXRSP {

    def apply[T <: CHIBundleRSP](gen: T)(implicit p: Parameters) = new CHIChannelRSP(gen)

    def apply()(implicit p: Parameters) = new CHIChannelRSP(new CHIBundleRSP)
}

// RXRSP Channel.
object CHIChannelRXRSP {
    
    def apply[T <: CHIBundleRSP](gen: T)(implicit p: Parameters) = Flipped(new CHIChannelRSP(gen))

    def apply()(implicit p: Parameters) = Flipped(new CHIChannelRSP(new CHIBundleRSP))
}
