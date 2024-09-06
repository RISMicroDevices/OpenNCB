package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.debug.DebugElement
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB Request CAM for transaction request-order maintainence.
*/
object NCBOrderRequestCAM {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]
}

class NCBOrderRequestCAM(implicit val p: Parameters)
        extends Module with WithCHIParameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBOrderRequestCAM.PublicParametersKey)
        .getOrElse(new NCBOrderRequestCAM.PublicParameters)

    // local parameters


    /*
    * Port I/O: Allocate
    */
    class AllocatePort extends Bundle {
        val en          = Input(Bool())
        val strb        = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Free
    */
    class FreePort extends Bundle {
        val strb        = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Query
    */
    class QueryPort extends Bundle {
        val resultValid = Output(Bool())
        val resultIndex = Output(UInt(paramNCB.outstandingIndexWidth.W))
    }


    /*
    * Module I/O
    */
    val io = IO(new Bundle {
        // allocate port
        val allocate        = new AllocatePort

        // free port
        val free            = new FreePort

        // query port
        val query           = new QueryPort
    })


    // CAM Registers
    class CAMEntry extends Bundle {
        val valid       = Bool()
        val index       = UInt(paramNCB.outstandingIndexWidth.W)
    }

    protected val regCAM    = RegInit(init = {
        val resetValue  = Wire(new CAMEntry)
        resetValue.valid    := false.B
        resetValue.index    := DontCare
        resetValue
    })

    // CAM update logic
    io.free.strb.zipWithIndex.foreach({ case (strb, i) => {
        when (strb && regCAM.index === i.U) {
            regCAM.valid    := false.B
        }
    }})

    when (io.allocate.en) {
        regCAM.valid    := true.B
        regCAM.index    := OHToUInt(io.allocate.strb)
    }


    // CAM query logic
    io.query.resultValid    := regCAM.valid
    io.query.resultIndex    := regCAM.index


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val AllocateNotOneHot           = Output(Bool())
        val DoubleAllocation            = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    @DebugElement
    protected val debugRegCAMFreeable   = RegInit(
        init = VecInit(Seq.fill(paramNCB.outstandingDepth)(false.B)))
    
    debugRegCAMFreeable.zipWithIndex.foreach({ case (entry, i) => {

        when (io.allocate.en & io.allocate.strb(i)) {
            entry   := true.B
        }

        when (io.free.strb(i)) {
            entry   := false.B
        }
    }})

    /*
    * @assertion AllocateNotOneHot 
    *   The allocation strobe must be one-hot. Only one exactly entry was allowed to be
    *   allocated at a time.
    */
    debug.AllocateNotOneHot := io.allocate.en && PopCount(io.allocate.strb) =/= 1.U
    assert(!debug.AllocateNotOneHot,
        "multiple or zero allocation")

    /*
    * @assertion DoubleAllocation
    *   A yet allocated entry was not allowed to be allocated again.
    */
    (0 until paramNCB.outstandingDepth).foreach(i => {
        debug.DoubleAllocation(i) := io.allocate.en && io.allocate.strb(i) && debugRegCAMFreeable(i)
        assert(!debug.DoubleAllocation(i),
            s"double allocation at [${i}]")
    })
}
