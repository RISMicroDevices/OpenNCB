package cn.rismd.openncb.chi.channel

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.bundle._


/*
* CHI SNP Channel.
*/
class CHIChannelSNP[+T <: CHIBundleSNP](gen: T)(implicit p: Parameters) extends CHIChannel[T](gen)


// TXSNP Channel.
object CHIChannelTXSNP {

    def apply[T <: CHIBundleSNP](gen: T)(implicit p: Parameters) = new CHIChannelSNP(gen)

    def apply()(implicit p: Parameters) = new CHIChannelSNP(new CHIBundleSNP)
}

// RXSNP Channel.
object CHIChannelRXSNP {
    
    def apply[T <: CHIBundleSNP](gen: T)(implicit p: Parameters) = Flipped(new CHIChannelSNP(gen))

    def apply()(implicit p: Parameters) = Flipped(new CHIChannelSNP(new CHIBundleSNP))
}

