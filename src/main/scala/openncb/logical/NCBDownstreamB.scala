package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.UIntToOH
import chisel3.util.MuxLookup
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.channel.AXI4ChannelMasterB
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP
import cc.xiangshan.openncb.chi.field.CHIFieldRespErr
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.CompanionConnection
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/* 
* NCB Downstream Port B
*/
object NCBDownstreamB {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uTransactionQueue     : NCBTransactionQueue,
              uDownstreamW          : NCBDownstreamW)
             (implicit p: Parameters) = {
        val u   = Module(new NCBDownstreamB(uTransactionQueue,
                                            uDownstreamW))

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.downstreamB

        // companion connection: NCBDownstreamW
        u.io.bid <> uDownstreamW.io.bid

        u
    }
}

class NCBDownstreamB(val uTransactionQueue  : NCBTransactionQueue,
                     val uDownstreamW       : NCBDownstreamW)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBDownstreamB.PublicParametersKey)
        .getOrElse(new NCBDownstreamB.PublicParameters)

    // local parameters


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // downstream B port (AXI domain)
        val b                   = AXI4ChannelMasterB()

        // internal-mapped transaction Response ID for B channel
        @CompanionConnection
        val bid                 = Flipped(chiselTypeOf(uDownstreamW.io.bid))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue               = Flipped(chiselTypeOf(uTransactionQueue.io.downstreamB))
    })

    // AXI Input Register
    val regB    = RegInit(init = {
        val resetValue  = Wire(Output(AXI4ChannelMasterB()))
        resetValue.ready    := DontCare // unused
        resetValue.valid    := false.B
        resetValue.bits     := DontCare
        resetValue
    })

    when (io.b.valid) {
        regB        := io.b
    }.otherwise {
        regB.valid  := false.B
    }


    // BID mapping
    protected val logicBIdOH    = {
        if (paramNCB.axiConstantAWID)
            VecInit(UIntToOH(io.bid.read.index, paramNCB.outstandingDepth).asBools)
        else
            VecInit(UIntToOH(regB.bits.id, paramNCB.outstandingDepth).asBools)
    }

    // error mapping
    protected val logicRespErr  = {
        if (paramNCB.writeNoError)
            CHIFieldRespErr.OK.U
        else
            MuxLookup(regB.bits.resp, CHIFieldRespErr.NDERR.U)(Seq(
                AXI4FieldRESP.OKAY  .U -> CHIFieldRespErr.OK    .U,
                AXI4FieldRESP.EXOKAY.U -> CHIFieldRespErr.EXOK  .U,
                AXI4FieldRESP.SLVERR.U -> CHIFieldRespErr.NDERR .U,
                AXI4FieldRESP.DECERR.U -> CHIFieldRespErr.NDERR .U
            ))
    }

    
    // ready output
    io.b.ready  := true.B

    // write response input
    io.bid.free.en          := regB.valid

    io.queue.opDone.strb    := ValidMux(regB.valid, logicBIdOH)

    if (paramNCB.writeNoError)
    {
        io.queue.operandWrite.strb              := VecInit.fill(paramNCB.outstandingDepth)(false.B)
        io.queue.operandWrite.bits.WriteRespErr := CHIFieldRespErr.OK.U
    }
    else
    {
        io.queue.operandWrite.strb              := ValidMux(regB.valid, logicBIdOH)
        io.queue.operandWrite.bits.WriteRespErr := logicRespErr
    }


    // assertions & debugs
    /*
    * Port I/O: Debug
    */
    class DebugPort extends DebugBundle {
        val DanglingAXIWriteResponse            = Output(Bool())
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion DanglingAXIWriteResponse
    *   Received Write Response on AXI B channel with no corresponding transaction.
    */
    debug.DanglingAXIWriteResponse := regB.valid && !io.bid.read.valid
    assert(!debug.DanglingAXIWriteResponse,
        "received BRESP without mapped BID")
}