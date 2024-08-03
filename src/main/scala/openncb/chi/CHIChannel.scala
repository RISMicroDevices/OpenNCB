package cn.rismd.openncb.chi

import chisel3._
import org.chipsalliance.cde.config.Parameters


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
}


// TX CHI Channel
object CHIChannelTX {
    def apply[T <: AbstractCHIBundle](gen: T)(implicit p: Parameters) = new CHIChannel(gen)
}

// RX CHI Channel
object CHIChannelRX {
    def apply[T <: AbstractCHIBundle](gen: T)(implicit p: Parameters) = Flipped(new CHIChannel(gen))
}
