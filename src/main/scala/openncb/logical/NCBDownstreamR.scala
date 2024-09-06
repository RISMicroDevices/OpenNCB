package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.UIntToOH
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP
import cc.xiangshan.openncb.axi.channel.AXI4ChannelMasterR
import cc.xiangshan.openncb.chi.field.CHIFieldRespErr
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.CompanionConnection
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB Downstream Port R 
*/
object NCBDownstreamR {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uTransactionQueue     : NCBTransactionQueue,
              uTransactionPayload   : NCBTransactionPayload,
              uDownstreamAr         : NCBDownstreamAR)
             (implicit p: Parameters) = {
        val u   = Module(new NCBDownstreamR(uTransactionQueue,
                                            uTransactionPayload,
                                            uDownstreamAr))

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.downstreamR

        // companion connection: NCBTransactionPayload
        u.io.payload <> uTransactionPayload.io.downstream.w

        // companion connection: NCBDownstreamAR
        u.io.rid <> uDownstreamAr.io.rid

        u
    }
}

class NCBDownstreamR(val uTransactionQueue      : NCBTransactionQueue,
                     val uTransactionPayload    : NCBTransactionPayload,
                     val uDownstreamAr          : NCBDownstreamAR)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBDownstreamR.PublicParametersKey)
        .getOrElse(new NCBDownstreamR.PublicParameters)

    // local parameters


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // downstream R port (AXI domain)
        val r                   = AXI4ChannelMasterR()

        // internal-mapped transaction Read ID for R channel
        @CompanionConnection
        val rid                 = Flipped(chiselTypeOf(uDownstreamAr.io.rid))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue               = Flipped(chiselTypeOf(uTransactionQueue.io.downstreamR))

        // from NCBTransactionPayload
        @CompanionConnection
        val payload             = Flipped(chiselTypeOf(uTransactionPayload.io.downstream.w))
    })

    // AXI Input Register
    val regR    = RegInit(init = {
        val resetValue  = Wire(Output(AXI4ChannelMasterR()))
        resetValue.ready    := DontCare // unused
        resetValue.valid    := false.B
        resetValue.bits     := DontCare
        resetValue
    })

    when (io.r.valid) {
        regR        := io.r
    }.otherwise {
        regR.valid  := false.B
    }


    // RID mapping
    protected val logicRIdOH    = {
        if (paramNCB.axiConstantAWID)
            VecInit(UIntToOH(io.rid.read.index, paramNCB.outstandingDepth).asBools)
        else
            VecInit(UIntToOH(regR.bits.id, paramNCB.outstandingDepth).asBools)
    }

    // error mapping
    protected val logicRespErr  = {
        if (paramNCB.writeNoError)
            CHIFieldRespErr.OK.U
        else
            MuxLookup(regR.bits.resp, CHIFieldRespErr.NDERR.U)(Seq(
                AXI4FieldRESP.OKAY  .U -> CHIFieldRespErr.OK    .U,
                AXI4FieldRESP.EXOKAY.U -> CHIFieldRespErr.EXOK  .U,
                AXI4FieldRESP.SLVERR.U -> CHIFieldRespErr.NDERR .U,
                AXI4FieldRESP.DECERR.U -> CHIFieldRespErr.NDERR .U
            ))
    }


    // ready output
    io.r.ready  := true.B

    /* 
    * read data input
    */
    val logicValidLast      = regR.valid &  regR.bits.last
    val logicValidNotLast   = regR.valid & !regR.bits.last

    io.queue.operandRead.strb   := logicRIdOH

    io.rid.free.en              := logicValidLast

    io.queue.opDone.strb    := ValidMux(logicValidLast, logicRIdOH)

    // chi operand update
    if (paramNCB.writeNoError)
    {
        io.queue.operandCHIWrite.strb               := VecInit.fill(paramNCB.outstandingDepth)(false.B)
        io.queue.operandCHIWrite.bits.ReadRespErr   := CHIFieldRespErr.OK.U
    }
    else
    {
        io.queue.operandCHIWrite.strb               := ValidMux(regR.valid, logicRIdOH)
        io.queue.operandCHIWrite.bits.ReadRespErr   := logicRespErr
    }

    // axi operand update
    io.queue.operandAXIWrite.strb           := ValidMux(logicValidNotLast, logicRIdOH)
    io.queue.operandAXIWrite.bits.Critical  := VecInit(
        io.queue.operandRead.bits.Critical.asUInt.rotateLeft(1).asBools
    )
    io.queue.operandAXIWrite.bits.Count     :=
        io.queue.operandRead.bits.Count - 1.U

    // payload write
    io.payload.en       := regR.valid
    io.payload.strb     := logicRIdOH
    io.payload.index    := io.queue.operandRead.bits.Critical
    io.payload.data     := regR.bits.data
    /**/


    // assertions & debug
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val DanglingAXIReadData                 = Output(Bool())
        val NotEnoughAXIReadDataBeat            = Output(Bool())
        val TooMuchAXIReadDataBeat              = Output(Bool())
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion DanglingAXIReadData
    *   Received Read Data on AXI R channel with no corresponding transaction. 
    */
    debug.DanglingAXIReadData := regR.valid && !io.rid.read.valid
    assert(!debug.DanglingAXIReadData,
        "received RDATA without mapped RID")

    /*
    * @assertion NotEnoughAXIReadDataBeat
    *   'RLAST' asserted before all data beat recevied. 
    */
    debug.NotEnoughAXIReadDataBeat := logicValidLast && io.queue.operandRead.bits.Count =/= 0.U
    assert(!debug.NotEnoughAXIReadDataBeat,
        "RDATA asserted before all RDATA received")

    /*
    * @assertion TooMuchAXIReadDataBeat
    *   'RLAST' not asserted when all data beat received.
    */
    debug.TooMuchAXIReadDataBeat := logicValidNotLast && io.queue.operandRead.bits.Count === 0.U
    assert(!debug.TooMuchAXIReadDataBeat,
        "RDATA not asserted on all RDATA received")
}
