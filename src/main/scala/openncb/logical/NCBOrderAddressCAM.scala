package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.chi.field.CHIFieldSize
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal
import cc.xiangshan.openncb.debug.DebugElement
import cc.xiangshan.openncb.util.ParallelMux
import cc.xiangshan.openncb.util.XZBarrier
import cc.xiangshan.openncb.util.ValidMux


/*
* NCB Address CAM for transaction address-order maintainence. 
*/
object NCBOrderAddressCAM {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]
}

class NCBOrderAddressCAM(implicit val p: Parameters)
        extends Module with WithCHIParameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBOrderAddressCAM.PublicParametersKey)
        .getOrElse(new NCBOrderAddressCAM.PublicParameters)

    // local parameters
    val paramMaskWidth      = log2Up(CHIFieldSize.Size64B.sizeInBytes)

    def paramCAMCapacity    = paramNCB.outstandingDepth


    /*
    * Port I/O: Allocate 
    * 
    * @io input     en      : Allocation Enable.
    * @io input     strb    : Allocation Strobe, one-hot addressing.
    * @io input     mask    : Allocation Address Mask, masking lower bits of maximum
    *                         address alignment width for overlapped comparsion.
    * @io input     addr    : Address Value, content-addressing.
    */
    class AllocatePort extends Bundle {
        val en          = Input(Bool())
        val strb        = Input(Vec(paramCAMCapacity, Bool()))
        val mask        = Input(UInt(paramMaskWidth.W))
        val addr        = Input(UInt(paramCHI.reqAddrWidth.W))
    }

    /*
    * Port I/O: Free
    * 
    * @io input     strb    : Free Strobe, one-hot addressing.
    */
    class FreePort extends Bundle {
        val strb        = Input(Vec(paramCAMCapacity, Bool()))
    }

    /*
    * Port I/O: Query 
    * 
    * @io input     mask        : Query Address Mask, masking lower bits of maximum
    *                             address alignment width for overlapped comparsion.
    * @io input     addr        : Query Address, content-addressing.
    * @io output    resultValid : Query Result Valid.
    * @io output    resultIndex : Query Result Index.
    */
    class QueryPort extends Bundle {
        val mask        = Input(UInt(paramMaskWidth.W))
        val addr        = Input(UInt(paramCHI.reqAddrWidth.W))
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
        val mask        = UInt(paramMaskWidth.W)
        val addr        = UInt(paramCHI.reqAddrWidth.W)
    }

    protected val regCAM  = RegInit(init = VecInit(Seq.fill(paramCAMCapacity){
        val resetValue  = Wire(new CAMEntry)
        resetValue.valid    := false.B
        resetValue.mask     := DontCare
        resetValue.addr     := DontCare
        resetValue
    }))

    


    // CAM comparators
    def funcCompare(a: UInt, b: UInt, mask: UInt) = {

        require(a.isWidthKnown && b.isWidthKnown && mask.isWidthKnown)
        require(a.getWidth == b.getWidth)

        val wireUpperA  = a.head(a.getWidth - mask.getWidth)
        val wireUpperB  = b.head(b.getWidth - mask.getWidth)

        val logicLowerA = a(mask.getWidth - 1, 0) & mask
        val logicLowerB = a(mask.getWidth - 1, 0) & mask

        (wireUpperA === wireUpperB) && (logicLowerA === logicLowerB)
    }


    // CAM update logic
    regCAM.zipWithIndex.foreach({ case (entry, i) => {

        // CAM entry allocate and update
        when (io.allocate.en) {
            when (io.allocate.strb(i)) {
                entry.valid := true.B
                entry.mask  := io.allocate.mask
                entry.addr  := io.allocate.addr

            }.elsewhen (funcCompare(entry.addr, io.allocate.addr, entry.mask & io.allocate.mask)) {
                entry.valid := false.B
            }
        }

        // CAM entry free
        when (io.free.strb(i)) {
            entry.valid := false.B
        }
    }})


    // CAM query logic
    val logicCAMHit = regCAM.map(entry => {
        ValidMux(entry.valid, 
            funcCompare(entry.addr, io.query.addr, entry.mask & io.query.mask))
    })

    io.query.resultValid    := VecInit(logicCAMHit).asUInt.orR
    io.query.resultIndex    := ParallelMux(logicCAMHit.zipWithIndex.map({ case(entry, i) => {
        (entry, i.U(paramNCB.outstandingIndexWidth.W))
    }}))


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val AllocateNotOneHot           = Output(Bool())
        val QueryResultMultipleHit      = Output(Bool())
        val DoubleAllocation            = Output(Vec(paramCAMCapacity, Bool()))
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    @DebugElement
    protected val debugRegCAMFreeable   = RegInit(
        init = VecInit(Seq.fill(paramCAMCapacity)(false.B)))

    debugRegCAMFreeable.zipWithIndex.foreach({ case (entry, i) => {

        when (io.free.strb(i)) {
            entry   := false.B
        }

        when (io.allocate.en & io.allocate.strb(i)) {
            entry   := true.B
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
    * @assertion QueryResultMultipleHit
    *   On query, no more than one entry was allowed to be hit.
    */
    private val debugWireQueryHit = regCAM.map(entry => {
        XZBarrier(entry.valid, funcCompare(entry.addr, io.query.addr, entry.mask & io.query.mask))
    })
    debug.QueryResultMultipleHit := PopCount(debugWireQueryHit) > 1.U
    assert(!debug.QueryResultMultipleHit,
        "multiple query result hit")

    /*
    * @assertion DoubleAllocation
    *   A yet allocated entry was not allowed to be allocated again.
    */
    (0 until paramCAMCapacity).foreach(i => {
        debug.DoubleAllocation(i) := io.allocate.en && io.allocate.strb(i) && (regCAM(i).valid || debugRegCAMFreeable(i))
        assert(!debug.DoubleAllocation(i),
            s"double allocation at [${i}]")
    })
}