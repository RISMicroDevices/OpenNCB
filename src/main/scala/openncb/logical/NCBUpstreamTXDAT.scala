package cn.rismd.openncb.logical

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.debug.CompanionConnection
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.chi.opcode.CHISNFOpcodesDAT
import cn.rismd.openncb.chi.channel.CHIChannelTXDAT
import cn.rismd.openncb.logical.chi.CHILinkActiveManagerTX
import cn.rismd.openncb.logical.chi.CHILinkCreditManagerTX
import cn.rismd.openncb.chi.CHIConstants
import cn.rismd.openncb.util.ValidMux
import chisel3.util.OHToUInt


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
        u.io.queueUpstream <> uTransactionQueue.io.upstreamTxDat

        // companion connection: NCBTransactionPayload
        u.io.queuePayloadRead   <> uTransactionPayload.io.upstream.r
        u.io.queuePayloadValid  <> uTransactionPayload.io.upstream.valid
        
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
        val queueUpstream           = Flipped(chiselTypeOf(uTransactionQueue.io.upstreamTxDat))

        // from NCBTransactionPayload
        @CompanionConnection
        val queuePayloadRead        = Flipped(chiselTypeOf(uTransactionPayload.io.upstream.r))
        
        @CompanionConnection
        val queuePayloadValid       = Flipped(chiselTypeOf(uTransactionPayload.io.upstream.valid))
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
    val logicOpCompDataReady    = VecInit(
        io.queueUpstream.opValid.valid.zipWithIndex.map({ case (valid, i) => {
            valid & VecInit((0 until paramMaxBeatCount).map(j => {
                io.queueUpstream.opValid.valid(i) &&
                io.queueUpstream.opValid.bits.Critical(i)(j) &&
                io.queuePayloadValid(i)(j)
            })).asUInt.orR
        }})
    )

    io.ageSelect.in := logicOpCompDataReady

    // transaction go op selection
    val logicOpDoneSelect   = Wire(chiselTypeOf(io.queueUpstream.opDone.bits))

    logicOpDoneSelect.CompData  := false.B

    when (io.queueUpstream.opRead.bits.CompData.valid) {
        logicOpDoneSelect.CompData  := true.B
    }

    // transaction go
    io.queueUpstream.opRead     .strb := io.ageSelect.out
    io.queueUpstream.infoRead   .strb := io.ageSelect.out
    io.queueUpstream.operandRead.strb := io.ageSelect.out

    io.queueUpstream.opDone.strb    := ValidMux(uLinkCredit.io.linkCreditAvailable, io.ageSelect.out)
    io.queueUpstream.opDone.bits    := logicOpDoneSelect

    io.queuePayloadRead.en      := logicOpDoneSelect.asUInt.orR
    io.queuePayloadRead.strb    := io.ageSelect.out
    io.queuePayloadRead.index   := io.queueUpstream.operandRead.bits.Critical

    // transaction go flit
    val logicOpDoneValid    = ValidMux(uLinkCredit.io.linkCreditAvailable,
        io.ageSelect.out.asUInt.orR)

    regTXDATFlitPend.flitv      := logicOpDoneValid
    when (logicOpDoneValid) {

        regTXDATFlitPend.flit.QoS           .get := io.queueUpstream.infoRead.bits.QoS
        regTXDATFlitPend.flit.TgtID         .get := {
            if (paramNCB.readCompDMT)
                io.queueUpstream.infoRead.bits.ReturnNID
            else
                io.queueUpstream.infoRead.bits.SrcID
        }
        regTXDATFlitPend.flit.SrcID         .get := io.queueUpstream.infoRead.bits.TgtID
        regTXDATFlitPend.flit.TxnID         .get := {
            if (paramNCB.readCompDMT)
                io.queueUpstream.infoRead.bits.ReturnTxnID
            else
                io.queueUpstream.infoRead.bits.TxnID
        }
        regTXDATFlitPend.flit.HomeNID       .get := {
            if (paramNCB.readCompDMT)
                io.queueUpstream.infoRead.bits.SrcID
            else
                paramNCB.readCompHomeNID.U
        }
        regTXDATFlitPend.flit.Opcode        .get := CompData.U
        regTXDATFlitPend.flit.RespErr       .get := io.queueUpstream.operandRead.bits.ReadRespErr
        regTXDATFlitPend.flit.Resp          .get := 0.U
        regTXDATFlitPend.flit.FwdState      (0.U)
        regTXDATFlitPend.flit.DataPull      (0.U)
        regTXDATFlitPend.flit.DataSource    (0.U) 
        regTXDATFlitPend.flit.DBID          .get := {
            if (paramNCB.readCompDMT)
                io.queueUpstream.infoRead.bits.TxnID
            else
                0.U
        }
        regTXDATFlitPend.flit.CCID          .get := io.queueUpstream.operandRead.bits.Addr(5, 4)
        regTXDATFlitPend.flit.DataID        .get := OHToUInt(io.queueUpstream.operandRead.bits.Critical)
        regTXDATFlitPend.flit.TraceTag      .get := 0.U
        regTXDATFlitPend.flit.BE            .get := 0.U
        regTXDATFlitPend.flit.Data          .get := io.queuePayloadRead.data
    }
}
