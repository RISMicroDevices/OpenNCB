package cn.rismd.openncb.chi.channel

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.bundle._


/*
* CHI Channel.
*/
class CHIChannel[+T <: AbstractCHIBundle](gen: T)(implicit p: Parameters) extends AbstractCHIChannel {

    // xFLITPEND    : Flit Pending.
    val flitpend    = Output(Bool())

    // xFLITV       : Flit Valid.
    val flitv       = Output(Bool())

    // xFLIT        : Flit.
    val flit        = Output(gen)

    // xLCRDV       : L-Credit Valid.
    val lcrdv       = Input(Bool())


    // utility functions
    def undirectedChiselType    = Output(chiselTypeOf(this))
}


// TX CHI Channel
object CHIChannelTX {
    def apply[T <: AbstractCHIBundle](gen: T)(implicit p: Parameters) = new CHIChannel(gen)
}

// RX CHI Channel
object CHIChannelRX {
    def apply[T <: AbstractCHIBundle](gen: T)(implicit p: Parameters) = Flipped(new CHIChannel(gen))
}
