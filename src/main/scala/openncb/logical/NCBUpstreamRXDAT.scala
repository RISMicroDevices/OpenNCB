package cn.rismd.openncb.logical

import chisel3._
import chisel3.util.UIntToOH
import chisel3.experimental.BundleLiterals._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.chi.opcode.CHISNFOpcodesDAT
import cn.rismd.openncb.chi.channel.CHIChannelRXDAT
import cn.rismd.openncb.chi.field.CHIFieldSize
import cn.rismd.openncb.chi.CHIConstants
import cn.rismd.openncb.debug.CompanionConnection
import cn.rismd.openncb.debug.DebugBundle
import cn.rismd.openncb.debug.DebugSignal
import cn.rismd.openncb.logical.chi.CHILinkActiveBundle
import cn.rismd.openncb.logical.chi.CHILinkActiveManagerRX
import cn.rismd.openncb.logical.chi.CHILinkCreditManagerRX
import cn.rismd.openncb.util.ValidMux


/* 
* NCB Upstream Port RXDAT
*/
object NCBUpstreamRXDAT {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uLinkActiveManager    : CHILinkActiveManagerRX,
              uTransactionQueue     : NCBTransactionQueue,
              uTransactionPayload   : NCBTransactionPayload)
             (implicit p: Parameters) = {
        val u   = Module(new NCBUpstreamRXDAT(uLinkActiveManager,
                                              uTransactionQueue,
                                              uTransactionPayload))

        // companion connection: LinkActiveManager
        u.io.linkState <> uLinkActiveManager.io.linkState

        // companion connection: NCBTransactionQueue
        u.io.queueUpstream <> uTransactionQueue.io.upstreamRxDat

        // companion connection: NCBTransactionPayload
        u.io.upstreamPayloadWrite <> uTransactionPayload.io.upstream.w

        u
    }
}

class NCBUpstreamRXDAT(val uLinkActiveManager       : CHILinkActiveManagerRX,
                       val uTransactionQueue        : NCBTransactionQueue,
                       val uTransactionPayload      : NCBTransactionPayload)
        (implicit val p: Parameters)
        extends Module with WithCHIParameters
                       with WithNCBParameters
                       with CHISNFOpcodesDAT {

    //
    def unsupportedCHIDataWidth(width: Int) =
        throw new IllegalArgumentException(s"unsupported CHI data width: ${width}")
    

    // public parameters
    val param   = p.lift(NCBUpstreamRXDAT.PublicParametersKey)
        .getOrElse(new NCBUpstreamRXDAT.PublicParameters)

    // local parameters
    protected def paramMaxBeatCount     = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramCHI.dataWidth


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // upstream RX DAT port (CHI domain)
        val rxdat                   = CHIChannelRXDAT()

        // from LinkActiveManagerRX
        @CompanionConnection
        val linkState               = Flipped(chiselTypeOf(uLinkActiveManager.io.linkState))

        // upstream query from NCBTransactionQueue
        @CompanionConnection
        val queueUpstream           = Flipped(chiselTypeOf(uTransactionQueue.io.upstreamRxDat))

        // upstream write to NCBTransactionPayload
        @CompanionConnection
        val upstreamPayloadWrite    = Flipped(chiselTypeOf(uTransactionPayload.io.upstream.w))
    })


    // Upstream RXDAT Input Register
    protected val regRXDAT  = RegInit(init = {
        val resetValue = Wire(io.rxdat.undirectedChiselType)
        resetValue.lcrdv    := false.B
        resetValue.flitpend := false.B
        resetValue.flitv    := false.B
        resetValue.flit     := DontCare
        resetValue
    })

    io.rxdat.lcrdv      := regRXDAT.lcrdv
    regRXDAT.flitpend   := io.rxdat.flitpend
    regRXDAT.flitv      := io.rxdat.flitv

    when (io.rxdat.flitv) {
        regRXDAT.flit := io.rxdat.flit
    }


    // Module: Link Credit Manager
    protected val uLinkCredit   = Module(new CHILinkCreditManagerRX(
        paramMaxCount           = paramNCB.outstandingDepth,
        paramInitialCount       = paramNCB.outstandingDepth,
        paramCycleBeforeSend    = 0,
        paramEnableMonitor      = true
    ))

    regRXDAT.lcrdv              := uLinkCredit.io.lcrdv

    uLinkCredit.io.linkState    := io.linkState

    // credit loop back. no credit limit required on RXDAT channel.
    val uLinkCreditProvideBuffer = uLinkCredit.attachLinkCreditProvideBuffer()
    uLinkCreditProvideBuffer.io.linkCreditProvide   := regRXDAT.flitv


    // Module: CHI Opcode Decode
    protected val uDecoder = Module(new Decoder(Seq(
    //  ========================
        DataLCrdReturn,
    //  ------------------------
        WriteDataCancel,
    //  ------------------------
        NonCopyBackWrData
    //  ========================
    )))

    uDecoder.io.valid   := regRXDAT.flitv
    uDecoder.io.opcode  := regRXDAT.flit.Opcode.get

    protected val logicTransactionData      = uDecoder.is(NonCopyBackWrData)
    protected val logicTransactionCancel    = uDecoder.is(WriteDataCancel)
    protected val logicLCrdReturn           = uDecoder.is(DataLCrdReturn)

    protected val logicTxnIDToStrb          = VecInit(
        UIntToOH(regRXDAT.flit.TxnID.get, paramNCB.outstandingDepth).asBools)

    
    // link credit consume on flit valid
    uLinkCredit.io.monitorCreditConsume := regRXDAT.flitv

    // link credit return by DataLCrdReturn
    uLinkCredit.io.monitorCreditReturn  := logicLCrdReturn


    // transaction payload write
    io.upstreamPayloadWrite.en      := logicTransactionData
    io.upstreamPayloadWrite.strb    := logicTxnIDToStrb
    io.upstreamPayloadWrite.index   := {
        if (paramMaxBeatCount == 4) {
            VecInit(UIntToOH(regRXDAT.flit.DataID.get, 4).asBools)
        } else if (paramMaxBeatCount == 2) {
            VecInit(UIntToOH(regRXDAT.flit.DataID.get(1, 1), 2).asBools)
        } else if (paramMaxBeatCount == 1) {
            VecInit(true.B)
        } else {
            unsupportedCHIDataWidth(paramCHI.dataWidth)
        }
    }
    io.upstreamPayloadWrite.data    := regRXDAT.flit.Data.get
    io.upstreamPayloadWrite.mask    := regRXDAT.flit.BE.get


    // transaction queue update
    io.queueUpstream.cancel.en      := logicTransactionCancel
    io.queueUpstream.cancel.strb    := logicTxnIDToStrb

    // transaction queue query (for debug)
    io.queueUpstream.query.en       := regRXDAT.flitv
    io.queueUpstream.query.strb     := logicTxnIDToStrb


    // assertions & debugs
    class DebugPort extends DebugBundle {
        // flat
        val TxnIDNonExist               = Output(Bool())
        val TxnIDOutOfRange             = Output(Bool())
        val WriteCancelOnNonPtl         = Output(Bool())
        val WriteCancelNotSupported     = Output(Bool())
        val WriteFullWithParitalBE      = Output(Bool())

        // submodule
        val linkCredit                  = chiselTypeOf(uLinkCredit.debug)
        val linkCreditProvide           = chiselTypeOf(uLinkCreditProvideBuffer.debug)
        val decoder                     = chiselTypeOf(uDecoder.debug)
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    debug.linkCredit        <> uLinkCredit.debug
    debug.linkCreditProvide <> uLinkCreditProvideBuffer.debug
    debug.decoder           <> uDecoder.debug

    /*
    * @assertion TxnIDNonExist
    *   Received a flit with TxnID with-in outstanding depth range but did not exist.
    */
    debug.TxnIDNonExist := regRXDAT.flitv && io.queueUpstream.query.result.valid
    assert(!debug.TxnIDNonExist,
        s"non-exist TxnID (no related transaction found)")

    /*
    * @assertion TxnIDOutOfRange
    *   Received a flit with TxnID out of outstanding depth range. 
    */
    debug.TxnIDOutOfRange := regRXDAT.flitv && regRXDAT.flit.TxnID.get >= paramNCB.outstandingDepth.U
    assert(!debug.TxnIDOutOfRange,
        s"non-exist TxnID (out of outstanding depth)")

    /*
    * @assertion WriteCancelOnNonPtl
    *   'WriteDataCancel' reply was only allowed for 'Write*Ptl'. 
    */
    debug.WriteCancelOnNonPtl := regRXDAT.flitv && logicTransactionCancel && !io.queueUpstream.query.result.WritePtl
    assert(!debug.WriteCancelOnNonPtl,
        s"WriteDataCancel only allowed for Write*Ptl")

    /*
    * @assertion WriteCancelNotSupported
    *   'WriteDataCancel' not supported when 'writeCancelable = false' configured. 
    */
    debug.WriteCancelNotSupported := regRXDAT.flitv && logicTransactionCancel && !paramNCB.writeCancelable.B
    assert(!debug.WriteCancelNotSupported,
        s"WriteDataCancel not supported (writeCancelable = false)")

    /* 
    * @assertion WriteFullWithParitalBE
    *   'BE' must be all asserted for 'Write*Full'.
    */
    debug.WriteFullWithParitalBE := regRXDAT.flitv && io.queueUpstream.query.result.WriteFull && !regRXDAT.flit.BE.get.andR
    assert(!debug.WriteFullWithParitalBE,
        s"partial BE on Write*Full")
}
