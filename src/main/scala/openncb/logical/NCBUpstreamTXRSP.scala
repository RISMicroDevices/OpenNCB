package cn.rismd.openncb.logical

import chisel3._
import chisel3.util.Cat
import chisel3.util.OHToUInt
import chisel3.util.log2Up
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.chi.opcode.CHISNFOpcodesRSP
import cn.rismd.openncb.chi.channel.CHIChannelTXRSP
import cn.rismd.openncb.logical.chi.CHILinkActiveManagerTX
import cn.rismd.openncb.logical.chi.CHILinkCreditManagerTX
import cn.rismd.openncb.debug.CompanionConnection
import cn.rismd.openncb.util.ValidMux
import cn.rismd.openncb.util.ParallelMux


/*
* NCB Upstream Port TXRSP
*/
object NCBUpstreamTXRSP {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uLinkActiveManager    : CHILinkActiveManagerTX,
              uTransactionAgeMatrix : NCBTransactionAgeMatrix,
              uTransactionQueue     : NCBTransactionQueue)
             (implicit p: Parameters) = {
        val u   = Module(new NCBUpstreamTXRSP(uLinkActiveManager,
                                              uTransactionAgeMatrix,
                                              uTransactionQueue))

        // companion connection: LinkActiveManager
        u.io.linkState <> uLinkActiveManager.io.linkState

        // companion connection: NCBTransactionAgeMatrix
        u.io.ageSelect <> uTransactionAgeMatrix.io.selectTXRSP
        
        // companion connection: NCBTransactionQueue
        u.io.queueUpstream <> uTransactionQueue.io.upstreamTxRsp

        u
    }
}

class NCBUpstreamTXRSP(val uLinkActiveManager       : CHILinkActiveManagerTX,
                       val uTransactionAgeMatrix    : NCBTransactionAgeMatrix,
                       val uTransactionQueue        : NCBTransactionQueue)
        (implicit val p: Parameters)
        extends Module with WithCHIParameters
                       with WithNCBParameters
                       with CHISNFOpcodesRSP {
    
    // public parameters
    val param   = p.lift(NCBUpstreamTXRSP.PublicParametersKey)
        .getOrElse(new NCBUpstreamTXRSP.PublicParameters)

    // local parameters


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // upstream TX RSP port (CHI domain)
        val txrsp                   = CHIChannelTXRSP()

        // from LinkActiveManagerTX
        @CompanionConnection
        val linkState               = Flipped(chiselTypeOf(uLinkActiveManager.io.linkState))

        // from NCBTransactionAgeMatrix
        @CompanionConnection
        val ageSelect               = Flipped(chiselTypeOf(uTransactionAgeMatrix.io.selectTXRSP))

        // upstream RSP port from NCBTransactionQueue
        @CompanionConnection
        val queueUpstream           = Flipped(chiselTypeOf(uTransactionQueue.io.upstreamTxRsp))
    })


    // Upstream TXRSP Pending Register
    protected val regTXRSPFlitPend  = RegInit(init = {
        val resetValue = Wire(io.txrsp.undirectedChiselType)
        resetValue.lcrdv    := DontCare // unused
        resetValue.flitpend := DontCare // unused
        resetValue.flitv    := false.B              // <--
        resetValue.flit     := DontCare             // <--
        resetValue
    })

    // Upstream TXRSP Output Register
    protected val regTXRSP  = RegInit(init = {
        val resetValue = Wire(io.txrsp.undirectedChiselType)
        resetValue.lcrdv    := false.B              // <--
        resetValue.flitpend := DontCare // unused
        resetValue.flitv    := false.B              // <== regTXRSPFlitPend
        resetValue.flit     := DontCare             // <== regTXRSPFlitPend
        resetValue
    })

    regTXRSP.flitv      := regTXRSPFlitPend.flitv
    when (regTXRSPFlitPend.flitv) {
        regTXRSP.flit   := regTXRSPFlitPend.flit
    }

    regTXRSP.lcrdv      := io.txrsp.lcrdv

    io.txrsp.flitpend   := regTXRSPFlitPend.flitv
    io.txrsp.flitv      := regTXRSP.flitv
    io.txrsp.flit       := regTXRSP.flit


    // Module: Link Credit Manager
    protected val uLinkCredit   = Module(new CHILinkCreditManagerTX)

    uLinkCredit.io.lcrdv        := regTXRSP.lcrdv
    uLinkCredit.io.linkState    := io.linkState

    // link credit consume on flit valid
    uLinkCredit.io.linkCreditConsume    := regTXRSPFlitPend.flitv

    // link credit return by RespLCrdReturn (not implemented)
    uLinkCredit.io.linkCreditReturn     := false.B


    // transaction valid to select
    io.ageSelect.in := io.queueUpstream.opValid.valid

    // transaction go op selection
    val logicOpDoneSelect   = Wire(chiselTypeOf(io.queueUpstream.opDone.bits))

    logicOpDoneSelect.Comp          := false.B
    logicOpDoneSelect.DBIDResp      := false.B
    logicOpDoneSelect.CompDBIDResp  := false.B
    logicOpDoneSelect.ReadReceipt   := false.B

    when (io.queueUpstream.opRead.bits.CompDBIDResp) {
        logicOpDoneSelect.CompDBIDResp  := true.B

    }.elsewhen (io.queueUpstream.opRead.bits.DBIDResp) {
        logicOpDoneSelect.DBIDResp      := true.B
    
    }.elsewhen (io.queueUpstream.opRead.bits.Comp) {
        logicOpDoneSelect.Comp          := true.B
    
    }.elsewhen (io.queueUpstream.opRead.bits.ReadReceipt) {
        logicOpDoneSelect.ReadReceipt   := true.B
    }

    // transaction go
    io.queueUpstream.opRead     .strb   := io.ageSelect.out
    io.queueUpstream.infoRead   .strb   := io.ageSelect.out
    io.queueUpstream.operandRead.strb   := io.ageSelect.out

    io.queueUpstream.opDone.strb    := ValidMux(uLinkCredit.io.linkCreditAvailable, io.ageSelect.out)
    io.queueUpstream.opDone.bits    := logicOpDoneSelect

    // transaction go flit
    val logicOpDoneValid    = ValidMux(uLinkCredit.io.linkCreditAvailable, 
        io.queueUpstream.opValid.valid.asUInt.orR)

    regTXRSPFlitPend.flitv      := logicOpDoneValid
    when (logicOpDoneValid) {
        regTXRSPFlitPend.flit.QoS       .get := io.queueUpstream.infoRead.bits.QoS
        regTXRSPFlitPend.flit.TgtID     .get := io.queueUpstream.infoRead.bits.SrcID
        regTXRSPFlitPend.flit.SrcID     .get := io.queueUpstream.infoRead.bits.TgtID
        regTXRSPFlitPend.flit.TxnID     .get := io.queueUpstream.infoRead.bits.TxnID
        regTXRSPFlitPend.flit.Opcode    .get := ParallelMux(Seq(
            (logicOpDoneSelect.Comp         , Comp          .U),
            (logicOpDoneSelect.DBIDResp     , DBIDResp      .U),
            (logicOpDoneSelect.CompDBIDResp , CompDBIDResp  .U),
            (logicOpDoneSelect.ReadReceipt  , ReadReceipt   .U)
        ))
        regTXRSPFlitPend.flit.RespErr   .get := io.queueUpstream.operandRead.bits.WriteRespErr
        regTXRSPFlitPend.flit.Resp      .get := 0.U
        regTXRSPFlitPend.flit.FwdState  (0.U)
        regTXRSPFlitPend.flit.DataPull  (0.U)
        regTXRSPFlitPend.flit.DBID      (Cat(
            0.U((paramCHI.rspDBIDWidth - log2Up(io.ageSelect.out.getWidth)).W),
            OHToUInt(io.ageSelect.out)))
        regTXRSPFlitPend.flit.PCrdType  .get := 0.U
        regTXRSPFlitPend.flit.TraceTag  .get := 0.U
    }
}
