package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.OHToUInt
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.chi.CHIConstants
import cc.xiangshan.openncb.chi.opcode.CHISNFOpcodesDAT
import cc.xiangshan.openncb.chi.channel.CHIChannelTXDAT
import cc.xiangshan.openncb.logical.chi.CHILinkActiveManagerTX
import cc.xiangshan.openncb.logical.chi.CHILinkCreditManagerTX
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.CompanionConnection
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB Upstream Port TXDAT 
*/
object NCBUpstreamTXDAT {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uLinkActiveManager    : CHILinkActiveManagerTX,
              uTransactionAgeMatrix : NCBTransactionAgeMatrix,
              uTransactionQueue     : NCBTransactionQueue,
              uTransactionPayload   : NCBTransactionPayload)
             (implicit p: Parameters) = {
        val u   = Module(new NCBUpstreamTXDAT(uLinkActiveManager,
                                              uTransactionAgeMatrix,
                                              uTransactionQueue,
                                              uTransactionPayload))
        
        // companion connection: LinkActiveManager
        u.io.linkState <> uLinkActiveManager.io.linkState

        // companion connection: NCBTransactionAgeMatrix
        u.io.ageSelect <> uTransactionAgeMatrix.io.selectTXDAT

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.upstreamTxDat

        // companion connection: NCBTransactionPayload
        u.io.payloadRead    <> uTransactionPayload.io.upstream.r
        u.io.payloadValid   <> uTransactionPayload.io.upstream.valid
        
        u
    }
}

class NCBUpstreamTXDAT(val uLinkActiveManager       : CHILinkActiveManagerTX,
                       val uTransactionAgeMatrix    : NCBTransactionAgeMatrix,
                       val uTransactionQueue        : NCBTransactionQueue,
                       val uTransactionPayload      : NCBTransactionPayload)
        (implicit val p: Parameters)
        extends Module with WithCHIParameters
                       with WithNCBParameters
                       with CHISNFOpcodesDAT {

    // public parameters
    val param   = p.lift(NCBUpstreamTXDAT.PublicParametersKey)
        .getOrElse(new NCBUpstreamTXDAT.PublicParameters)

    // local parameters
    def paramMaxBeatCount       = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramCHI.dataWidth


    /*
    * Module I/O
    */
    val io = IO(new Bundle {
        // upstream TX DAT port (CHI domain)
        val txdat                   = CHIChannelTXDAT()

        // from LinkActiveManagerTX
        @CompanionConnection
        val linkState               = Flipped(chiselTypeOf(uLinkActiveManager.io.linkState))

        // from NCBTransactionAgeMatrix
        @CompanionConnection
        val ageSelect               = Flipped(chiselTypeOf(uTransactionAgeMatrix.io.selectTXRSP))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue                   = Flipped(chiselTypeOf(uTransactionQueue.io.upstreamTxDat))

        // from NCBTransactionPayload
        @CompanionConnection
        val payloadRead             = Flipped(chiselTypeOf(uTransactionPayload.io.upstream.r))
        
        @CompanionConnection
        val payloadValid            = Flipped(chiselTypeOf(uTransactionPayload.io.upstream.valid))
    })


    // Upstream TXDAT Pending Register
    protected val regTXDATFlitPend  = RegInit(init = {
        val resetValue = Wire(io.txdat.undirectedChiselType)
        resetValue.lcrdv    := DontCare // unused
        resetValue.flitpend := DontCare // unused
        resetValue.flitv    := false.B              // <--
        resetValue.flit     := DontCare             // <--
        resetValue
    })

    // Upstream TXDAT Output Register
    protected val regTXDAT  = RegInit(init = {
        val resetValue = Wire(io.txdat.undirectedChiselType)
        resetValue.lcrdv    := false.B              // <--
        resetValue.flitpend := DontCare // unused
        resetValue.flitv    := false.B              // <== regTXDATFlitPend
        resetValue.flit     := DontCare             // <== regTXDATFlitPend
        resetValue
    })

    regTXDAT.flitv      := regTXDATFlitPend.flitv
    when (regTXDATFlitPend.flitv) {
        regTXDAT.flit   := regTXDATFlitPend.flit
    }

    regTXDAT.lcrdv      := io.txdat.lcrdv

    io.txdat.flitpend   := regTXDATFlitPend.flitv
    io.txdat.flitv      := regTXDAT.flitv
    io.txdat.flit       := regTXDAT.flit

    
    // Module: Link Credit Manager
    protected val uLinkCredit   = Module(new CHILinkCreditManagerTX)

    uLinkCredit.io.lcrdv        := regTXDAT.lcrdv
    uLinkCredit.io.linkState    := io.linkState

    // link credit consume on flit valid
    uLinkCredit.io.linkCreditConsume    := regTXDATFlitPend.flitv

    // link credit return by DataLCrdReturn (not implemented)
    uLinkCredit.io.linkCreditReturn     := false.B

    
    // transaction valid to select
    protected val logicOpCompDataReady  = VecInit(
        io.queue.opValid.valid.zipWithIndex.map({ case (valid, i) => {
            valid & VecInit((0 until paramMaxBeatCount).map(j => {
                io.queue.opValid.valid(i) &&
                io.queue.opValid.bits.Critical(i)(j) &&
                io.payloadValid(i)(j)
            })).asUInt.orR
        }})
    )

    io.ageSelect.in := logicOpCompDataReady

    // transaction go op selection
    protected val logicOpDoneValid    = ValidMux(uLinkCredit.io.linkCreditAvailable,
        io.ageSelect.out.asUInt.orR)

    protected val logicOpDoneSelect = Wire(chiselTypeOf(io.queue.opDone.bits))

    logicOpDoneSelect.CompData  := false.B

    when (io.queue.opRead.bits.CompData.valid) {
        logicOpDoneSelect.CompData  := io.queue.operandRead.bits.Count === 0.U
    }

    // transaction go
    val logicSelectedGo = ValidMux(uLinkCredit.io.linkCreditAvailable, io.ageSelect.out)

    io.queue.opRead     .strb   := io.ageSelect.out
    io.queue.infoRead   .strb   := io.ageSelect.out
    io.queue.operandRead.strb   := io.ageSelect.out

    io.queue.opDone.strb    := logicSelectedGo
    io.queue.opDone.bits    := logicOpDoneSelect

    // transaction payload read
    io.payloadRead.en       := logicOpDoneValid
    io.payloadRead.strb     := io.ageSelect.out
    io.payloadRead.index    := io.queue.operandRead.bits.Critical

    // transaction operand update
    io.queue.operandWrite.strb          := logicSelectedGo
    io.queue.operandWrite.bits.Critical := VecInit(
        io.queue.operandRead.bits.Critical.asUInt.rotateLeft(1).asBools
    )
    io.queue.operandWrite.bits.Count    :=
        io.queue.operandRead.bits.Count - 1.U

    // transaction go flit
    regTXDATFlitPend.flitv      := logicOpDoneValid
    when (logicOpDoneValid) {

        regTXDATFlitPend.flit.QoS           .get := io.queue.infoRead.bits.QoS
        regTXDATFlitPend.flit.TgtID         .get := {
            if (paramNCB.readCompDMT)
                io.queue.infoRead.bits.ReturnNID
            else
                io.queue.infoRead.bits.SrcID
        }
        regTXDATFlitPend.flit.SrcID         .get := io.queue.infoRead.bits.TgtID
        regTXDATFlitPend.flit.TxnID         .get := {
            if (paramNCB.readCompDMT)
                io.queue.infoRead.bits.ReturnTxnID
            else
                io.queue.infoRead.bits.TxnID
        }
        regTXDATFlitPend.flit.HomeNID       .get := {
            if (paramNCB.readCompDMT)
                io.queue.infoRead.bits.SrcID
            else
                paramNCB.readCompHomeNID.U
        }
        regTXDATFlitPend.flit.Opcode        .get := CompData.U
        regTXDATFlitPend.flit.RespErr       .get := io.queue.operandRead.bits.ReadRespErr
        regTXDATFlitPend.flit.Resp          .get := 0.U
        regTXDATFlitPend.flit.FwdState      (0.U)
        regTXDATFlitPend.flit.DataPull      (0.U)
        regTXDATFlitPend.flit.DataSource    (0.U) 
        regTXDATFlitPend.flit.DBID          .get := {
            if (paramNCB.readCompDMT)
                io.queue.infoRead.bits.TxnID
            else
                0.U
        }
        regTXDATFlitPend.flit.CCID          .get := io.queue.operandRead.bits.Addr(5, 4)
        regTXDATFlitPend.flit.DataID        .get := OHToUInt(io.queue.operandRead.bits.Critical)
        regTXDATFlitPend.flit.TraceTag      .get := 0.U
        regTXDATFlitPend.flit.BE            .get := 0.U
        regTXDATFlitPend.flit.Data          .get := io.payloadRead.data
    }


    // assertions & debugs
    /*
    * Port I/O: Debug
    */
    class DebugPort extends DebugBundle {
        // submodule
        val linkCredit                      = chiselTypeOf(uLinkCredit.debug)
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    debug.linkCredit <> uLinkCredit.debug
}
