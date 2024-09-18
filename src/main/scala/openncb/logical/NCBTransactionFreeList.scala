package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.PopCount
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB Transaction allocation control by Free List. 
*/
object NCBTransactionFreeList {

    case class Parameters (
    )

    case object ParametersKey extends Field[Parameters]
}

class NCBTransactionFreeList(implicit val p: Parameters) 
        extends Module with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBTransactionFreeList.ParametersKey)
        .getOrElse(() => new NCBTransactionFreeList.Parameters)

    // local parameters


    /* 
    * Port I/O: Transaction Allocation
    * 
    * @io input     en      : Allocation Enable.
    * @io output    strb    : Allocation Strobe, one-hot selected.
    */
    class AllocatePort extends Bundle {
        val en          = Input(Bool())
        val strb        = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Transaction Free 
    * 
    * @io input     en      : Free Enable.
    * @io output    strb    : Free Strobe.
    */
    class FreePort extends Bundle {
        val strb        = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // allocation port
        val allocate    = new AllocatePort

        // free port
        val free        = new FreePort

        // empty signal
        val empty       = Output(Bool())
    })


    // One-Hot Free Flag Registers
    protected val regFree     = RegInit(VecInit((Seq.fill(paramNCB.outstandingDepth)(true.B))))

    // one-hot selection logic
    private var logicFreeCarry  = Seq.fill(paramNCB.outstandingDepth + 1)(WireInit(false.B))
    private val logicFreeOut    = VecInit(regFree.zipWithIndex.map({ case (u, i) => {
        val uout = u & !logicFreeCarry(i)
        logicFreeCarry(i + 1) := logicFreeCarry(i) | uout
        uout
    } }))

    // free flag update
    (0 until paramNCB.outstandingDepth).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regFree(i)  := false.B
        }

        when (io.free.strb(i)) {
            regFree(i)  := true.B
        }
    })

    assert(PopCount(logicFreeOut) <= 1.U, "NCB Internal Error: free list selection not one-hot")


    // allocation output
    io.allocate.strb    := logicFreeOut

    // empty signal
    io.empty    := regFree.asUInt.andR


    // assertions & debugs
    /* 
    * Port I/O: Debug
    */
    class DebugPort extends DebugBundle {
        val FreeListUnderflow           = Output(Bool())
        val DoubleFreeOrCorruption      = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    @DebugSignal
    val debug = IO(new DebugPort)

    /*
    * @assertion FreeListUnderflow 
    *   Allocation was not allowed on empty free list.
    */
    debug.FreeListUnderflow := io.allocate.en && !io.allocate.strb.asUInt.orR
    assert(!debug.FreeListUnderflow,
        "free list allocation underflow")

    /*
    * @assertion DoubleFreeOrCorruption 
    *   Free operation was not allowed on free slots.
    */
    (0 until paramNCB.outstandingDepth).foreach(i => {
        debug.DoubleFreeOrCorruption(i) := io.free.strb(i) && regFree(i)
        assert(!debug.DoubleFreeOrCorruption(i),
            s"free list double free or corruption at ${i}")
    })
}
