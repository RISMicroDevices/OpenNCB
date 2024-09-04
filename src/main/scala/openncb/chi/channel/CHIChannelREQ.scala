package cn.rismd.openncb.chi.channel

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.bundle._


/*
* CHI REQ Channel.
*/
class CHIChannelREQ[+T <: CHIBundleREQ](gen: T) extends CHIChannel[T](gen)


// TXREQ Channel.
object CHIChannelTXREQ {

    def apply[T <: CHIBundleREQ](gen: T) = new CHIChannelREQ(gen)

    def apply()(implicit p: Parameters) = new CHIChannelREQ(new CHIBundleREQ)
}

// RXREQ Channel.
object CHIChannelRXREQ {

    def apply[T <: CHIBundleREQ](gen: T) = Flipped(new CHIChannelREQ(gen))

    def apply()(implicit p: Parameters) = Flipped(new CHIChannelREQ(new CHIBundleREQ))
}
