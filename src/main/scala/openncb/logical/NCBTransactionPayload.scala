package cn.rismd.openncb.logical

import chisel3._
import chisel3.util.log2Up
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.VecLiterals._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.axi.WithAXI4Parameters
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.util._
import cn.rismd.openncb.chi.CHIConstants
import chisel3.util.Cat
import cn.rismd.openncb.debug.DebugBundle
import cn.rismd.openncb.debug.DebugSignal


/*
* NCB Transaction Data Payload.
*/
class NCBTransactionPayload(implicit val p: Parameters) 
        extends Module with WithAXI4Parameters 
                       with WithCHIParameters 
                       with WithNCBParameters {

    // public parameters
    case class Parameters (
    )

    case object ParametersKey extends Field[Parameters]

    val param   = p.lift(ParametersKey).getOrElse(() => new Parameters)


    // local parameters
    protected def paramPayloadCapacity              = paramNCB.outstandingDepth
    protected def paramPayloadAddressWidth          = log2Up(paramNCB.outstandingDepth)

    protected def paramUpstreamMaxBeatCount         = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramCHI.dataWidth
    protected def paramUpstreamIndexWidth           = log2Up(paramUpstreamMaxBeatCount)
    protected def paramUpstreamDataWidth            = paramCHI.dataWidth
    protected def paramUpstreamMaskWidth            = paramUpstreamDataWidth / 8

    protected def paramDownstreamMaxBeatCount       = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramAXI4.dataWidth
    protected def paramDownstreamIndexWidth         = log2Up(paramDownstreamMaxBeatCount)
    protected def paramDownstreamDataWidth          = paramAXI4.dataWidth
    protected def paramDownstreamMaskWidth          = paramDownstreamDataWidth / 8

    protected val paramMaxPayloadDataWidth          = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH
    protected val paramMaxPayloadByteWidth          = paramMaxPayloadDataWidth / 8
    protected val paramMaxPayloadMaskWidth          = paramMaxPayloadDataWidth / 8

    protected def paramPayloadSlotDataWidth         = paramCHI.dataWidth min paramAXI4.dataWidth
    protected def paramPayloadSlotDataCount         = paramMaxPayloadDataWidth / paramPayloadSlotDataWidth
    protected def paramPayloadSlotMaskWidth         = paramPayloadSlotDataWidth / 8
    protected def paramPayloadSlotMaskCount         = paramPayloadSlotDataCount

    protected def paramSlotCountUpstream            = paramMaxPayloadDataWidth / paramUpstreamDataWidth
    protected def paramSlotCountDownstream          = paramMaxPayloadByteWidth / paramDownstreamDataWidth

    protected def paramSlotCatCountUpstream         = paramUpstreamDataWidth / paramPayloadSlotDataWidth
    protected def paramSlotCatCountDownstream       = paramDownstreamDataWidth / paramPayloadSlotDataWidth


    // parameter checks
    require(paramUpstreamMaskWidth == paramCHI.datBEWidth,
        s"NCB Internal Error: mask width mismatch with BE: ${paramUpstreamMaskWidth} =/= ${paramCHI.datBEWidth}")


    /*
    * Port I/O: Upstream (CHI domain)
    * 
    * @io input     wen     : Write Enable.
    * @io input     waddr   : Write Address, addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     windex  : Write Index, addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io input     wdata   : Write Data.
    * @io input     wmask   : Write Mask, comes from BE.
    * 
    * @io input     ren     : Read Enable.
    * @io input     raddr   : Read Address, addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     rindex  : Read index, addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io output    rdata   : Read Data.
    */
    class UpstreamPort extends Bundle {
        // write signals
        val wen             = Input(Bool())
        val waddr           = Input(UInt(paramPayloadAddressWidth.W))
        val windex          = Input(UInt(paramUpstreamIndexWidth.W))
        val wdata           = Input(UInt(paramUpstreamDataWidth.W))
        val wmask           = Input(UInt(paramUpstreamMaskWidth.W))

        // read signals
        val ren             = Input(Bool())
        val raddr           = Input(UInt(paramPayloadAddressWidth.W))
        val rindex          = Input(UInt(paramUpstreamIndexWidth.W))
        val rdata           = Output(UInt(paramUpstreamDataWidth.W))

        // valid signals
        val valid           = Output(Vec(paramPayloadCapacity, Bool()))
    }

    /*
    * Port I/O: Downstream (AXI4 domain) 
    * 
    * @io input     wen     : Write Enable.
    * @io input     waddr   : Write Address, addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     windex  : Write Index, addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io input     wdata   : Write Data.
    * @io input     wlast   : Write Data Last, comes from RLAST.
    * 
    * @io input     ren     : Read Enable.
    * @io input     raddr   : Read Address, addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     rindex  : Read Index, addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io output    rdata   : Read Data.
    * @io output    rmask   : Read Mask, which comes from BE, goes to WSTRB.
    */
    class DownstreamPort extends Bundle {
        // write signals
        val wen             = Input(Bool())
        val waddr           = Input(UInt(paramPayloadAddressWidth.W))
        val windex          = Input(UInt(paramDownstreamIndexWidth.W))
        val wdata           = Input(UInt(paramDownstreamDataWidth.W))
        val wlast           = Input(Bool())

        // read signals
        val ren             = Input(Bool())
        val raddr           = Input(UInt(paramPayloadAddressWidth.W))
        val rindex          = Input(UInt(paramDownstreamIndexWidth.W))
        val rdata           = Output(UInt(paramDownstreamDataWidth.W))
        val rmask           = Output(UInt(paramDownstreamMaskWidth.W))

        // valid signals
        val valid           = Vec(paramPayloadCapacity, Vec(paramUpstreamMaxBeatCount, Bool()))
    }

    /*
    * Port I/O: Payload Entry Allocation (w/ Debug)
    * 
    * @io input     en      : Allocation Enable.
    * @io input     addr    : Allocation Address.
    * @io input     upload  : Allocation Direction, 'Upload' when asserted, otherwise 'Download'.
    *                         - Upload   : AXI to CHI
    *                         - Download : CHI to AXI
    */
    class AllocatePort extends Bundle {
        val en              = Input(Bool())
        val addr            = Input(UInt(paramPayloadAddressWidth.W))
        val upload          = Input(Bool())
    }

    /*
    * Port I/O: Payload Entry Free (w/ Debug)
    * 
    * @io input     en      : Free Enable.
    * @io input     addr    : Free Address.
    */
    class FreePort extends Bundle {
        val en              = Input(Bool())
        val addr            = Input(UInt(paramPayloadAddressWidth.W))
    }


    /*
    * Module I/O
    * 
    * @io bundle    upstream    : See {@code NCBTransactionPayload#UpstreamPort}.
    * @io bundle    downstream  : See {@code NCBTransactionPayload#DownstreamPort}.
    * @io bundle    allocate    : See {@code NCBTransactionPayload#AllocatePort}.
    * @io bundle    free        : See {@code NCBTransactionPayload#FreePort}.
    * @io bundle    debug       : See {@code NCBTransactionPayload#DebugPort}.
    */
    val io = IO(new Bundle {
        // upstream read/write port (CHI domain)
        val upstream    = new UpstreamPort

        // downstream read/write port (AXI4 domain)
        val downstream  = new DownstreamPort

        // transaction allocation
        val allocate    = new AllocatePort

        // transaction free
        val free        = new FreePort

        // debug port
        @DebugSignal
        val debug       = new DebugPort
    })


    // Status Payload - Upstream (CHI to AXI) Valid Registers
    val regUpstreamValid    = RegInit(Vec(paramPayloadCapacity, Vec(paramUpstreamMaxBeatCount, Bool())), 
        init = VecInit.fill(paramPayloadCapacity, paramUpstreamMaxBeatCount)(false.B))

    when (io.allocate.en) {
        regUpstreamValid(io.allocate.addr)  := VecInit.fill(paramUpstreamMaxBeatCount)(false.B)
    }

    when (io.upstream.wen) {
        regUpstreamValid(io.upstream.waddr)(io.upstream.windex) := true.B
    }
    
    // Status Payload - Downstream (AXI to CHI) Valid Registers
    val regDownstreamValid  = RegInit(Vec(paramPayloadCapacity, Bool()), 
        init = VecInit(Seq.fill(paramPayloadCapacity)(false.B)))

    when (io.allocate.en) {
        regDownstreamValid(io.allocate.addr)    := false.B
    }

    when (io.downstream.wen && io.downstream.wlast) {
        regDownstreamValid(io.downstream.waddr) := true.B
    }


    // Data Payload - Data Registers
    val regData = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotDataCount, UInt(paramPayloadSlotDataWidth.W))))

    when (io.upstream.wen) {

        if (paramSlotCatCountUpstream == 1)
            regData(io.upstream.waddr)(io.upstream.windex) := io.upstream.wdata
        else
            (0 until paramSlotCatCountUpstream).foreach(i => {
                regData(io.upstream.waddr)(Cat(io.upstream.windex, i.U(log2Up(paramSlotCatCountUpstream).W))) :=
                    io.upstream.wdata.extract(i, paramPayloadSlotDataWidth)
            })
    }

    when (io.downstream.wen) {
    
        if (paramSlotCatCountDownstream == 1)
            regData(io.downstream.waddr)(io.downstream.windex) := io.downstream.wdata
        else
            (0 until paramSlotCatCountDownstream).foreach(i => {
                regData(io.downstream.waddr)(Cat(io.downstream.windex, i.U(log2Up(paramSlotCatCountDownstream).W))) :=
                    io.downstream.wdata.extract(i, paramPayloadSlotDataWidth)
            })
    }

    // Data Payload - Mask Registers
    val regMask = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotMaskCount, UInt(paramPayloadSlotMaskWidth.W))))

    when (io.upstream.wen) {

        if (paramSlotCatCountUpstream == 1)
            regMask(io.upstream.waddr)(io.upstream.windex) := io.upstream.wmask
        else
            (0 until paramSlotCatCountUpstream).foreach(i => {
                regMask(io.upstream.waddr)(Cat(io.upstream.windex, i.U(log2Up(paramSlotCatCountUpstream).W))) :=
                    io.upstream.wdata.extract(i, paramPayloadSlotMaskWidth)
            })
    }


    // read concation and connections for payload data and mask registers
    val wireDataVecUpstream     = Wire(Vec(paramPayloadCapacity, 
        Vec(paramUpstreamMaxBeatCount, UInt(paramUpstreamDataWidth.W))))

    val wireDataVecDownstream   = Wire(Vec(paramPayloadCapacity,
        Vec(paramDownstreamMaxBeatCount, UInt(paramDownstreamDataWidth.W))))

    val wireMaskVecUpstream     = Wire(Vec(paramPayloadCapacity,
        Vec(paramUpstreamMaxBeatCount, UInt(paramUpstreamMaskWidth.W))))

    val wireMaskVecDownstream   = Wire(Vec(paramPayloadCapacity,
        Vec(paramDownstreamMaxBeatCount, UInt(paramDownstreamMaskWidth.W))))

    val funcCat = (i: Int, j: Int, k: Int, t: (Vec[Vec[UInt]], Vec[Vec[UInt]])) => {
        t._1(i)(j) := Cat((0 until k).map(l => {
            t._2(i)(j * k + l)
        }).reverse)
    }

    (0 until paramPayloadCapacity).foreach(i => {

        (0 until paramUpstreamMaxBeatCount).foreach(j => {
            Seq((wireDataVecUpstream, regData),
                (wireMaskVecUpstream, regMask))
            .foreach(t => funcCat(i, j, paramSlotCatCountUpstream, t))
        })

        (0 until paramDownstreamMaxBeatCount).foreach(j => {
            Seq((wireDataVecDownstream, regData),
                (wireMaskVecDownstream, regMask))
            .foreach(t => funcCat(i, j, paramSlotCatCountDownstream, t))
        })
    })


    // upstream outputs
    io.upstream.rdata   := wireDataVecUpstream(io.upstream.raddr)(io.upstream.rindex)

    io.upstream.valid   := regDownstreamValid

    // downstream outputs
    io.downstream.rdata := wireDataVecDownstream(io.downstream.raddr)(io.downstream.rindex)
    io.downstream.rmask := wireMaskVecDownstream(io.downstream.raddr)(io.downstream.rindex)

    io.downstream.valid := regUpstreamValid


    // Debug Info - Transaction Allocation Table
    val regTransactionTable = RegInit(new Bundle {
        val valid       = Vec(paramPayloadCapacity, Bool())
        val upload      = Vec(paramPayloadCapacity, Bool())
    }.Lit(
        _.valid     -> false.BVecLit(paramPayloadCapacity)  // reset 'valid' to 0
    ))

    when (io.allocate.en) {
        regTransactionTable.valid   (io.allocate.addr)  := true.B
        regTransactionTable.upload  (io.allocate.addr)  := io.allocate.upload
    }

    when (io.free.en) {
        regTransactionTable.valid   (io.free.addr)      := false.B
    }


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val DoubleAllocationException               = Output(Bool())
        val DoubleFreeOrCorruptionException         = Output(Bool())
        val DualWriteConfliction                    = Output(Bool())
        val DualReadConfliction                     = Output(Bool())
        val UpstreamWriteOutOfBound                 = Output(Bool())
        val UpstreamReadOutOfBound                  = Output(Bool())
        val DownstreamWriteOutOfBound               = Output(Bool())
        val DownstreamReadOutOfBound                = Output(Bool())
        val UpstreamWriteDirectionConfliction       = Output(Bool())
        val UpstreamReadDirectionConfliction        = Output(Bool())
        val DownstreamWriteDirectionConfliction     = Output(Bool())
        val DownstreamReadDirectionConfliction      = Output(Bool())
    }

    /*
    * @assertion DoubleAllocationException
    *   One slot in Transaction Payload must only be allocated once util next free.
    */
    io.debug.DoubleAllocationException := io.allocate.en && regTransactionTable.valid(io.allocate.addr)
    assert(!io.debug.DoubleAllocationException,
        "double allocation")

    /*
    * @assertion DoubleFreeOrCorruptionException
    *   One slot in Transaction Payload must only be freed once, and a previous
    *   allocation must have been performed.
    */
    io.debug.DoubleFreeOrCorruptionException := io.free.en && !regTransactionTable.valid(io.free.addr)
    assert(!io.debug.DoubleFreeOrCorruptionException,
        "double free or corruption")

    /*
    * @assertion TransactionDualWriteConfliction 
    *   In one transaction, it's not allowed to write payload simultaneously from both
    *   downstream and upstream.
    */
    io.debug.DualWriteConfliction := io.upstream.wen && io.downstream.wen.orR && io.upstream.waddr === io.downstream.waddr
    assert(!io.debug.DualWriteConfliction,
        "payload write confliction by downstream and upstream")

    /*
    * @assertion TransactionDualReadConfliction 
    *   In one transaction, it's not allowed to read payload simultaneously from both
    *   downstream and upstream.
    */
    io.debug.DualReadConfliction := io.upstream.ren && io.upstream.ren && io.upstream.raddr === io.upstream.raddr
    assert(!io.debug.DualReadConfliction,
        "payload read confliction by downstream and upstream")

    /* 
    * @assertion UpstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    io.debug.UpstreamWriteOutOfBound := io.upstream.wen && !regTransactionTable.valid(io.upstream.waddr)
    assert(!io.debug.UpstreamWriteOutOfBound,
        "payload upstream write on non-exist transaction")

    /*
    * @assertion UpstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    io.debug.UpstreamReadOutOfBound := io.upstream.ren && !regTransactionTable.valid(io.upstream.raddr)
    assert(!io.debug.UpstreamReadOutOfBound,
        "payload upstream read on non-exist transaction")

    /*
    * @assertion DownstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    io.debug.DownstreamWriteOutOfBound := io.downstream.wen.orR && !regTransactionTable.valid(io.downstream.waddr)
    assert(!io.debug.DownstreamWriteOutOfBound,
        "payload downstream write on non-exist transaction")

    /*
    * @assertion DownstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    io.debug.DownstreamReadOutOfBound := io.downstream.ren && !regTransactionTable.valid(io.downstream.raddr)
    assert(!io.debug.DownstreamReadOutOfBound,
        "payload downstream read on non-exist transaction")

    /*
    * @assertion UpstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    *   e.g. CHI Write Transaction => (CHI Upstream) Write-only <-PAYLOAD-> Read-only (Downstream AXI)
    */
    io.debug.UpstreamWriteDirectionConfliction := io.upstream.wen && regTransactionTable.upload(io.upstream.waddr)
    assert(!io.debug.UpstreamWriteDirectionConfliction,
        "payload upstream write direction conflict")

    /*
    * @assertion UpstreamReadDirectionConfliction
    *   Payload read direction must be the same as transaction direction. 
    */
    io.debug.UpstreamReadDirectionConfliction := io.upstream.ren && !regTransactionTable.upload(io.upstream.raddr)
    assert(!io.debug.UpstreamReadDirectionConfliction,
        "payload upstream read direction conflict")

    /*
    * @assertion DownstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    */
    io.debug.DownstreamWriteDirectionConfliction := io.downstream.wen.orR && !regTransactionTable.upload(io.downstream.waddr)
    assert(!io.debug.DownstreamWriteDirectionConfliction,
        "payload downstream write direction conflict")

    /* 
    * @assertion DownstreamReadDirectionConfliction 
    *   Payload read direction must be the same as transaction direction. 
    */
    io.debug.DownstreamReadDirectionConfliction := io.downstream.ren && regTransactionTable.upload(io.downstream.raddr)
    assert(!io.debug.DownstreamReadDirectionConfliction,
        "payload downstream read direction conflict")
}
