package cn.rismd.openncb.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


/*
* CHI REQ Channel.
*/
class CHIChannelREQ[+T <: CHIBundleREQ](gen: T)(implicit p: Parameters) extends CHIChannel[T](gen)


// TXREQ Channel.
object CHIChannelTXREQ {

    def apply[T <: CHIBundleREQ](gen: T)(implicit p: Parameters) = new CHIChannelREQ(gen)

    def apply()(implicit p: Parameters) = new CHIChannelREQ(new CHIBundleREQ)
}

// RXREQ Channel.
object CHIChannelRXREQ {

    def apply[T <: CHIBundleREQ](gen: T)(implicit p: Parameters) = Flipped(new CHIChannelREQ(gen))

    def apply()(implicit p: Parameters) = Flipped(new CHIChannelREQ(new CHIBundleREQ))
}
