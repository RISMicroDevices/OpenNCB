package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.OHToUInt
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.bundle.AXI4BundleAW
import cc.xiangshan.openncb.axi.channel.AXI4ChannelMasterAW
import cc.xiangshan.openncb.logical.shared.SpillRegister
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.CompanionConnection


/*
* NCB Downstream Port AW 
*/
object NCBDownstreamAW {
    
    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uTransactionAgeMatrix : NCBTransactionAgeMatrix,
              uTransactionQueue     : NCBTransactionQueue)
             (implicit p: Parameters) = {
        val u   = Module(new NCBDownstreamAW(uTransactionAgeMatrix,
                                             uTransactionQueue))
        
        // companion connection: NCBTransactionAgeMatrix
        u.io.ageSelect <> uTransactionAgeMatrix.io.selectAW

        // companion connection: NCBTransactionQueue
        u.io.queue <> uTransactionQueue.io.downstreamAw

        u
    }
}

class NCBDownstreamAW(val uTransactionAgeMatrix : NCBTransactionAgeMatrix,
                      val uTransactionQueue     : NCBTransactionQueue)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBDownstreamAW.PublicParametersKey)
        .getOrElse(new NCBDownstreamAW.PublicParameters)

    // local parameters


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // downstream AW port (AXI domain)
        val aw                  = AXI4ChannelMasterAW()

        // internal-mapped transaction Write ID for W channel
        val wid                 = new Bundle {
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
        val ageSelect           = Flipped(chiselTypeOf(uTransactionAgeMatrix.io.selectAW))

        // from NCBTransactionQueue
        @CompanionConnection
        val queue               = Flipped(chiselTypeOf(uTransactionQueue.io.downstreamAw))
    })


    // Module: WID FIFO
    protected val uWId   = Module(new NCBTransactionIndexFIFO)

    io.wid.read.valid   := uWId.io.query.valid
    io.wid.read.index   := uWId.io.query.index

    uWId.io.free.en     := io.wid.free.en

    
    // Module: AW Channel Output Spill Register
    protected val wireSpillAW   = SpillRegister.attachOut(io.aw)

    // Module: Op Done Spill Register
    protected val uSpillOpDone  = SpillRegister(chiselTypeOf(io.queue.opDone.strb))


    // task valid to select
    io.ageSelect.in := io.queue.opValid.valid

    // task go
    io.queue.infoRead   .strb   := io.ageSelect.out
    io.queue.operandRead.strb   := io.ageSelect.out

    io.queue.opPoNR.strb    := ValidMux(wireSpillAW.ready, io.ageSelect.out)

    uWId.io.allocate.en     := wireSpillAW.fire
    uWId.io.allocate.index  := OHToUInt(io.ageSelect.out)

    // task go fields
    wireSpillAW.valid       := io.ageSelect.out.asUInt.orR

    wireSpillAW.bits.id     := {
        if (paramNCB.axiConstantAWID)
            paramNCB.axiConstantAWIDValue.U
        else
            OHToUInt(io.ageSelect.out)
    }
    wireSpillAW.bits.addr   := io.queue.operandRead.bits.Addr
    wireSpillAW.bits.len    := io.queue.operandRead.bits.Len
    wireSpillAW.bits.size   := io.queue.operandRead.bits.Size
    wireSpillAW.bits.burst  := io.queue.operandRead.bits.Burst
    wireSpillAW.bits.lock   := 0.U
    wireSpillAW.bits.cache  := {
        if (paramNCB.axiAWBufferable)
            "b0011".U
        else
            "b0010".U
    }
    wireSpillAW.bits.prot   := "b010".U
    wireSpillAW.bits.qos    := {
        if (paramNCB.axiConstantAWQoS)
            paramNCB.axiConstantAWQoSValue.U
        else
            io.queue.infoRead.bits.QoS
    }
    wireSpillAW.bits.region := paramNCB.axiAWRegionValue.U

    // task done
    uSpillOpDone.io.in.valid    := RegNext(next = wireSpillAW.fire, init = false.B) // logical correction delay
    uSpillOpDone.io.in.bits     := RegNext(next = io.ageSelect.out)                 // logical correction delay
    uSpillOpDone.io.out.ready   := RegNext(next = io.aw.ready     , init = false.B) // bus timing isolation

    io.queue.opDone.strb    := ValidMux(uSpillOpDone.io.out.fire, uSpillOpDone.io.out.bits)
}
