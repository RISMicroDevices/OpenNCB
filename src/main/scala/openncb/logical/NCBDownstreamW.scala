package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.UIntToOH
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.channel.AXI4ChannelMasterW
import cc.xiangshan.openncb.debug.CompanionConnection
import cc.xiangshan.openncb.logical.shared.SpillRegister
import cc.xiangshan.openncb.util.ValidMux


/*
* NCB Downstream Port W 
*/
object NCBDownstreamW {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uTransactionQueue     : NCBTransactionQueue,
              uTransactionPayload   : NCBTransactionPayload,
              uDownstreamAw         : NCBDownstreamAW)
             (implicit p: Parameters) = {
        val u   = Module(new NCBDownstreamW(uTransactionQueue,
                                            uTransactionPayload,
                                            uDownstreamAw))

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.downstreamW

        // companion connection: NCBTransactionPayload
        u.io.payloadRead    <> uTransactionPayload.io.downstream.r
        u.io.payloadValid   <> uTransactionPayload.io.downstream.valid

        // companion connection: NCBDownstreamAW
        u.io.wid <> uDownstreamAw.io.wid

        u
    }
}

class NCBDownstreamW(val uTransactionQueue      : NCBTransactionQueue,
                     val uTransactionPayload    : NCBTransactionPayload,
                     val uDownstreamAw          : NCBDownstreamAW)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBDownstreamW.PublicParametersKey)
        .getOrElse(new NCBDownstreamW.PublicParameters)

    // local parameters

    
    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // downstream W port (AXI domain)
        val w                   = AXI4ChannelMasterW()

        // internal-mapped transaction Response ID for B channel
        val bid                 = new Bundle {
            //
            val read                = new Bundle {
                val valid               = Output(Bool())
                val index               = Output(UInt(paramNCB.outstandingIndexWidth.W))
            }

            //
            val free                = new Bundle {
                val en                  = Input(Bool())
            }
        }

        // internal-mapped transaction Write ID from W channel
        @CompanionConnection
        val wid                 = Flipped(chiselTypeOf(uDownstreamAw.io.wid))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue               = Flipped(chiselTypeOf(uTransactionQueue.io.downstreamW))

        // downstream read to NCBTransactionPayload
        @CompanionConnection
        val payloadRead         = Flipped(chiselTypeOf(uTransactionPayload.io.downstream.r))

        // downstream valid from NCBTransactionPayload
        @CompanionConnection
        val payloadValid        = Flipped(chiselTypeOf(uTransactionPayload.io.downstream.valid))
    })

    
    // Module: BID FIFO
    protected val uBId  = Module(new NCBTransactionIndexFIFO)

    io.bid.read.valid   := uBId.io.query.valid
    io.bid.read.index   := uBId.io.query.index

    uBId.io.free.en     := io.bid.free.en

    uBId.io.allocate.en     := io.wid.free.en
    uBId.io.allocate.index  := io.wid.read.index

    
    // Module: W Channel Output Spill Register
    protected val wireSpillW    = SpillRegister.attachOut(io.w)

    // Module: Op Done Spill Register
    protected val uSpillOpDone  = SpillRegister(chiselTypeOf(io.queue.opDone.strb))


    // task go
    protected val logicWIdOH    = ValidMux(io.wid.read.valid, 
        VecInit(UIntToOH(io.wid.read.index, paramNCB.outstandingDepth).asBools))

    io.queue.operandRead.strb   := logicWIdOH

    // task payload read
    io.payloadRead.en       := io.wid.read.valid
    io.payloadRead.strb     := logicWIdOH
    io.payloadRead.index    := io.queue.operandRead.bits.Critical

    /*
    * task point of reservation
    */
    protected val logicTaskPoR      = VecInit(
        logicWIdOH.zipWithIndex.map({ case (strb, i) => {
            VecInit(io.queue.operandRead.bits.Critical.zipWithIndex.map({ case (critical, j) => {
                critical & io.payloadValid(i)(j)
            }})).asUInt.orR & strb
        }})
    )

    protected val logicTaskLast     = io.queue.operandRead.bits.Count === 0.U

    // task go fields
    wireSpillW.valid        := logicTaskPoR.asUInt.orR

    wireSpillW.bits.data    := io.payloadRead.data
    wireSpillW.bits.strb    := io.payloadRead.mask
    wireSpillW.bits.last    := logicTaskLast
    /**/

    /*
    * task point of no return
    */
    protected val logicTaskPoNR         = VecInit(
        logicTaskPoR.map(_ & wireSpillW.ready)
    )

    protected val logicTaskPoNRLast     = VecInit(
        logicTaskPoNR.map(_ &  logicTaskLast)
    )

    protected val logicTaskPoNRNotLast  = VecInit(
        logicTaskPoNR.map(_ & !logicTaskLast)
    )

    // task done
    io.queue.opPoNR.strb    := logicTaskPoNRLast

    // task wid update
    io.wid.free.en  := logicTaskPoNRLast.asUInt.orR

    // task operand update
    io.queue.operandWrite.strb          := logicTaskPoNRNotLast
    io.queue.operandWrite.bits.Critical := VecInit(
        io.queue.operandRead.bits.Critical.asUInt.rotateLeft(1).asBools
    )
    io.queue.operandWrite.bits.Count    :=
        io.queue.operandRead.bits.Count - 1.U
    /**/

    // task done
    uSpillOpDone.io.in.valid    := RegNext(next = wireSpillW.fire, init = false.B) // logical correction delay
    uSpillOpDone.io.in.bits     := RegNext(next = logicWIdOH)                      // logical correction delay
    uSpillOpDone.io.out.ready   := RegNext(next = io.w.ready     , init = false.B) // bus timing isolation

    io.queue.opDone.strb    := ValidMux(uSpillOpDone.io.out.fire, uSpillOpDone.io.out.bits)
}