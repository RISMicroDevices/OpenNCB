package cn.rismd.openncb.logical

import chisel3._
import chisel3.util.OHToUInt
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.debug.CompanionConnection
import cn.rismd.openncb.axi.WithAXI4Parameters
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.axi.channel.AXI4ChannelMasterAR
import cn.rismd.openncb.logical.shared.SpillRegister
import cn.rismd.openncb.util.ValidMux


/* 
* NCB Downstream Port AR
*/
object NCBDownstreamAR {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uTransactionAgeMatrix : NCBTransactionAgeMatrix,
              uTransactionQueue     : NCBTransactionQueue)
             (implicit p: Parameters) = {
        val u   = Module(new NCBDownstreamAR(uTransactionAgeMatrix,
                                             uTransactionQueue))

        // companion connection: NCBTransactionAgeMatrix
        u.io.ageSelect <> uTransactionAgeMatrix.io.selectAR

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.downstreamAr

        u
    }
}

class NCBDownstreamAR(val uTransactionAgeMatrix : NCBTransactionAgeMatrix,
                      val uTransactionQueue     : NCBTransactionQueue)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBDownstreamAR.PublicParametersKey)
        .getOrElse(new NCBDownstreamAR.PublicParameters)

    // local parameters


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // downstream AR port (AXI domain)
        val ar                  = AXI4ChannelMasterAR()

        // internal-mapped transaction Read ID for R channel
        val rid                 = new Bundle {
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

        // from NCBTransactionAgeMatrix
        @CompanionConnection
        val ageSelect               = Flipped(chiselTypeOf(uTransactionAgeMatrix.io.selectAR))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue                   = Flipped(chiselTypeOf(uTransactionQueue.io.downstreamAr))
    })


    // Module: RID FIFO
    protected val uRId  = Module(new NCBTransactionIndexFIFO)

    io.rid.read.valid   := uRId.io.query.valid
    io.rid.read.index   := uRId.io.query.index

    uRId.io.free.en     := io.rid.free.en


    // Module: AR Channel Output Spill Register
    protected val wireSpillAR   = SpillRegister.attachOut(io.ar)

    // Module: Op Done Spill Register
    protected val uSpillOpDone  = SpillRegister(chiselTypeOf(io.queue.opDone.strb))


    // task valid to select
    io.ageSelect.in := io.queue.opValid.valid

    // task go
    io.queue.infoRead   .strb   := io.ageSelect.out
    io.queue.operandRead.strb   := io.ageSelect.out

    io.queue.opPoNR.strb    := ValidMux(wireSpillAR.ready, io.ageSelect.out)

    uRId.io.allocate.en     := wireSpillAR.fire
    uRId.io.allocate.index  := OHToUInt(io.ageSelect.out)

    // task go fields
    wireSpillAR.valid       := io.ageSelect.out.asUInt.orR

    wireSpillAR.bits.id     := {
        if (paramNCB.axiConstantARID)
            paramNCB.axiConstantARIDValue.U
        else
            OHToUInt(io.ageSelect.out)
    }
    wireSpillAR.bits.addr   := io.queue.operandRead.bits.Addr
    wireSpillAR.bits.len    := io.queue.operandRead.bits.Len
    wireSpillAR.bits.size   := io.queue.operandRead.bits.Size
    wireSpillAR.bits.burst  := io.queue.operandRead.bits.Burst
    wireSpillAR.bits.lock   := 0.U
    wireSpillAR.bits.cache  := {
        if (paramNCB.axiARBufferable)
            "b0011".U
        else
            "b0010".U
    }
    wireSpillAR.bits.prot   := "b010".U
    wireSpillAR.bits.qos    := {
        if (paramNCB.axiConstantARQoS)
            paramNCB.axiConstantAWQoSValue.U
        else
            io.queue.infoRead.bits.QoS
    }
    wireSpillAR.bits.region := paramNCB.axiARRegionValue.U

    // task done
    uSpillOpDone.io.in.valid    := RegNext(next = wireSpillAR.fire, init = false.B) // logical correction delay
    uSpillOpDone.io.in.bits     := RegNext(next = io.ageSelect.out)                 // logical correction delay
    uSpillOpDone.io.out.ready   := RegNext(next = io.ar.ready     , init = false.B) // bus timing isolation

    io.queue.opDone.strb    := ValidMux(uSpillOpDone.io.out.fire, uSpillOpDone.io.out.bits)
}
