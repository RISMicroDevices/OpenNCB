package cn.rismd.openncb.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI RSP Channel.
*/
class CHIChannelRSP[+T <: CHIBundleRSP](gen: T)(implicit p: Parameters) extends CHIChannel[T](gen)


// TXRSP Channel.
object CHIChannelTXRSP {
    def apply[T <: CHIBundleRSP](gen: T)(implicit p: Parameters) = new CHIChannelRSP(gen)
}

// RXRSP Channel.
object CHIChannelRXRSP {
    def apply[T <: CHIBundleRSP](gen: T)(implicit p: Parameters) = Flipped(new CHIChannelRSP(gen))
}
