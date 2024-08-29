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
object NCBTransactionPayload {

    case class Parameters (
    )

    case object ParametersKey extends Field[Parameters]
}

class NCBTransactionPayload(implicit val p: Parameters)
        extends Module with WithAXI4Parameters 
                       with WithCHIParameters 
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBTransactionPayload.ParametersKey)
        .getOrElse(() => new NCBTransactionPayload.Parameters)


    // local parameters
    protected def paramPayloadCapacity              = paramNCB.outstandingDepth

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
    * @io input     wstrb   : Write Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     windex  : Write Index, addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io input     wdata   : Write Data.
    * @io input     wmask   : Write Mask, comes from BE.
    * 
    * @io input     ren     : Read Enable.
    * @io input     rstrb   : Read Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     rindex  : Read index, addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io output    rdata   : Read Data.
    */
    class UpstreamPort extends Bundle {
        // write signals
        val wen             = Input(Bool())
        val wstrb           = Input(Vec(paramPayloadCapacity, Bool()))
        val windex          = Input(UInt(paramUpstreamIndexWidth.W))
        val wdata           = Input(UInt(paramUpstreamDataWidth.W))
        val wmask           = Input(UInt(paramUpstreamMaskWidth.W))

        // read signals
        val ren             = Input(Bool())
        val rstrb           = Input(Vec(paramPayloadCapacity, Bool()))
        val rindex          = Input(UInt(paramUpstreamIndexWidth.W))
        val rdata           = Output(UInt(paramUpstreamDataWidth.W))

        // valid signals
        val valid           = Output(Vec(paramPayloadCapacity, Bool()))
    }

    /*
    * Port I/O: Downstream (AXI4 domain) 
    * 
    * @io input     wen     : Write Enable.
    * @io input     wstrb   : Write Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     windex  : Write Index, addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io input     wdata   : Write Data.
    * @io input     wlast   : Write Data Last, comes from RLAST.
    * 
    * @io input     ren     : Read Enable.
    * @io input     rstrb   : Read Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     rindex  : Read Index, addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io output    rdata   : Read Data.
    * @io output    rmask   : Read Mask, which comes from BE, goes to WSTRB.
    */
    class DownstreamPort extends Bundle {
        // write signals
        val wen             = Input(Bool())
        val wstrb           = Input(Vec(paramPayloadCapacity, Bool()))
        val windex          = Input(UInt(paramDownstreamIndexWidth.W))
        val wdata           = Input(UInt(paramDownstreamDataWidth.W))
        val wlast           = Input(Bool())

        // read signals
        val ren             = Input(Bool())
        val rstrb           = Input(Vec(paramPayloadCapacity, Bool()))
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
    * @io input     strb    : Allocation Strobe, one-hot.
    * @io input     upload  : Allocation Direction, 'Upload' when asserted, otherwise 'Download'.
    *                         - Upload   : AXI to CHI
    *                         - Download : CHI to AXI
    */
    class AllocatePort extends Bundle {
        val en              = Input(Bool())
        val strb            = Input(Vec(paramPayloadCapacity, Bool()))
        val upload          = Input(Bool())
    }

    /*
    * Port I/O: Payload Entry Free (w/ Debug)
    * 
    * @io input     en      : Free Enable.
    * @io input     strb    : Free Strobe, one-hot.
    */
    class FreePort extends Bundle {
        val en              = Input(Bool())
        val strb            = Input(Vec(paramPayloadCapacity, Bool()))
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
    })


    // Status Payload - Upstream (CHI to AXI) Valid Registers
    val regUpstreamValid    = RegInit(Vec(paramPayloadCapacity, Vec(paramUpstreamMaxBeatCount, Bool())), 
        init = VecInit.fill(paramPayloadCapacity, paramUpstreamMaxBeatCount)(false.B))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regUpstreamValid(i) := VecInit.fill(paramUpstreamMaxBeatCount)(false.B)
        }

        when (io.upstream.wen & io.upstream.wstrb(i)) {
            regUpstreamValid(i)(io.upstream.windex) := true.B
        }
    })

    // Status Payload - Downstream (AXI to CHI) Valid Registers
    val regDownstreamValid  = RegInit(Vec(paramPayloadCapacity, Bool()), 
        init = VecInit(Seq.fill(paramPayloadCapacity)(false.B)))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regDownstreamValid(i)   := false.B
        }

        when (io.downstream.wen & io.downstream.wlast & io.downstream.wstrb(i)) {
            regDownstreamValid(i)   := true.B
        }
    })


    // Data Payload - Data Registers
    val regData = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotDataCount, UInt(paramPayloadSlotDataWidth.W))))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.upstream.wen & io.upstream.wstrb(i)) {

            if (paramSlotCatCountUpstream == 1)
                regData(i)(io.upstream.windex)  := io.upstream.wdata
            else
                (0 until paramSlotCatCountUpstream).foreach(i => {
                    regData(i)(Cat(io.upstream.windex, i.U(log2Up(paramSlotCatCountUpstream).W))) :=
                        io.upstream.wdata.extract(i, paramPayloadSlotDataWidth)
                })
        }

        when (io.downstream.wen & io.downstream.wstrb(i)) {
    
        if (paramSlotCatCountDownstream == 1)
            regData(i)(io.downstream.windex)    := io.downstream.wdata
        else
            (0 until paramSlotCatCountDownstream).foreach(i => {
                regData(i)(Cat(io.downstream.windex, i.U(log2Up(paramSlotCatCountDownstream).W))) :=
                    io.downstream.wdata.extract(i, paramPayloadSlotDataWidth)
            })
        }
    })

    

    // Data Payload - Mask Registers
    val regMask = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotMaskCount, UInt(paramPayloadSlotMaskWidth.W))))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.upstream.wen & io.upstream.wstrb(i)) {

            if (paramSlotCatCountUpstream == 1)
                regMask(i)(io.upstream.windex)  := io.upstream.wmask
            else
                (0 until paramSlotCatCountUpstream).foreach(i => {
                    regMask(i)(Cat(io.upstream.windex, i.U(log2Up(paramSlotCatCountUpstream).W))) :=
                        io.upstream.wdata.extract(i, paramPayloadSlotMaskWidth)
                })
        }
    })

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
    io.upstream.rdata   := ParallelMux(
        wireDataVecUpstream.zipWithIndex.map(t => (io.upstream.rstrb(t._2), t._1))
    )(io.upstream.rindex)

    io.upstream.valid   := regDownstreamValid

    // downstream outputs
    io.downstream.rdata := ParallelMux(
        wireDataVecDownstream.zipWithIndex.map(t => (io.downstream.rstrb(t._2), t._1))
    )(io.downstream.rindex)

    io.downstream.rmask := ParallelMux(
        wireMaskVecDownstream.zipWithIndex.map(t => (io.downstream.rstrb(t._2), t._1))
    )(io.downstream.rindex)

    io.downstream.valid := regUpstreamValid


    // Debug Info - Transaction Allocation Table
    val regTransactionTable = RegInit(new Bundle {
        val valid       = Vec(paramPayloadCapacity, Bool())
        val upload      = Vec(paramPayloadCapacity, Bool())
    }.Lit(
        _.valid     -> false.BVecLit(paramPayloadCapacity)  // reset 'valid' to 0
    ))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regTransactionTable.valid   (i) := true.B
            regTransactionTable.upload  (i) := io.allocate.upload
        }

        when (io.free.en & io.free.strb(i)) {
            regTransactionTable.valid   (i) := false.B
        }
    })


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        val DoubleAllocationException               = Output(Vec(paramPayloadCapacity, Bool()))
        val DoubleFreeOrCorruptionException         = Output(Vec(paramPayloadCapacity, Bool()))
        val DualWriteConfliction                    = Output(Vec(paramPayloadCapacity, Bool()))
        val DualReadConfliction                     = Output(Vec(paramPayloadCapacity, Bool()))
        val UpstreamWriteOutOfBound                 = Output(Vec(paramPayloadCapacity, Bool()))
        val UpstreamReadOutOfBound                  = Output(Vec(paramPayloadCapacity, Bool()))
        val DownstreamWriteOutOfBound               = Output(Vec(paramPayloadCapacity, Bool()))
        val DownstreamReadOutOfBound                = Output(Vec(paramPayloadCapacity, Bool()))
        val UpstreamWriteDirectionConfliction       = Output(Vec(paramPayloadCapacity, Bool()))
        val UpstreamReadDirectionConfliction        = Output(Vec(paramPayloadCapacity, Bool()))
        val DownstreamWriteDirectionConfliction     = Output(Vec(paramPayloadCapacity, Bool()))
        val DownstreamReadDirectionConfliction      = Output(Vec(paramPayloadCapacity, Bool()))
    }
    
    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion DoubleAllocationException
    *   One slot in Transaction Payload must only be allocated once util next free.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DoubleAllocationException(i) := io.allocate.en && io.allocate.strb(i) && regTransactionTable.valid(i)
        assert(!debug.DoubleAllocationException(i),
            s"double allocation at [${i}]")
    })

    /*
    * @assertion DoubleFreeOrCorruptionException
    *   One slot in Transaction Payload must only be freed once, and a previous
    *   allocation must have been performed.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DoubleFreeOrCorruptionException(i) := io.free.en && io.free.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.DoubleFreeOrCorruptionException(i),
            s"double free or corruption at [${i}]")
    })

    /*
    * @assertion TransactionDualWriteConfliction 
    *   In one transaction, it's not allowed to write payload simultaneously from both
    *   downstream and upstream.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DualWriteConfliction(i) := io.upstream.wen && io.upstream.wstrb(i) && io.downstream.wen && io.downstream.wstrb(i)
        assert(!debug.DualWriteConfliction(i),
            s"payload write confliction by downstream and upstream at [${i}]")
    })

    /*
    * @assertion TransactionDualReadConfliction 
    *   In one transaction, it's not allowed to read payload simultaneously from both
    *   downstream and upstream.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DualReadConfliction(i) := io.upstream.ren && io.upstream.rstrb(i) && io.downstream.ren && io.downstream.rstrb(i)
        assert(!debug.DualReadConfliction(i),
            s"payload read confliction by downstream and upstream at [${i}]")
    })

    /* 
    * @assertion UpstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamWriteOutOfBound(i) := io.upstream.wen && io.upstream.wstrb(i) && !regTransactionTable.valid(i)
        assert(!debug.UpstreamWriteOutOfBound(i),
            s"payload upstream write on non-exist transaction at [${i}]")
    })

    /*
    * @assertion UpstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamReadOutOfBound(i) := io.upstream.ren && io.upstream.rstrb(i) && !regTransactionTable.valid(i)
        assert(!debug.UpstreamReadOutOfBound(i),
            s"payload upstream read on non-exist transaction at [${i}]")
    })

    /*
    * @assertion DownstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamWriteOutOfBound(i) := io.downstream.wen && io.downstream.wstrb(i) && !regTransactionTable.valid(i)
        assert(!debug.DownstreamWriteOutOfBound(i),
            s"payload downstream write on non-exist transaction at [${i}]")
    })

    /*
    * @assertion DownstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamReadOutOfBound(i) := io.downstream.ren && io.downstream.rstrb(i) && !regTransactionTable.valid(i)
        assert(!debug.DownstreamReadOutOfBound(i),
            s"payload downstream read on non-exist transaction at [${i}]")
    })

    /*
    * @assertion UpstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    *   e.g. CHI Write Transaction => (CHI Upstream) Write-only <-PAYLOAD-> Read-only (Downstream AXI)
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamWriteDirectionConfliction(i) := io.upstream.wen && io.upstream.wstrb(i) && regTransactionTable.upload(i)
        assert(!debug.UpstreamWriteDirectionConfliction(i),
            s"payload upstream write direction conflict at [${i}]")
    })

    /*
    * @assertion UpstreamReadDirectionConfliction
    *   Payload read direction must be the same as transaction direction. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamReadDirectionConfliction(i) := io.upstream.ren && io.upstream.rstrb(i) && !regTransactionTable.upload(i)
        assert(!debug.UpstreamReadDirectionConfliction(i),
            s"payload upstream read direction conflict at [${i}]")
    })

    /*
    * @assertion DownstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamWriteDirectionConfliction(i) := io.downstream.wen && io.downstream.wstrb(i) && !regTransactionTable.upload(i)
        assert(!debug.DownstreamWriteDirectionConfliction(i),
            s"payload downstream write direction conflict at [${i}]")
    })

    /* 
    * @assertion DownstreamReadDirectionConfliction 
    *   Payload read direction must be the same as transaction direction. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamReadDirectionConfliction(i) := io.downstream.ren && io.downstream.rstrb(i) && regTransactionTable.upload(i)
        assert(!debug.DownstreamReadDirectionConfliction(i),
            s"payload downstream read direction conflict at [${i}]")
    })
}
