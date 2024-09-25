package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.log2Up
import chisel3.util.Cat
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.VecLiterals._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.chi.CHIConstants
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.util._
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


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

    //
    protected def isPower2Mult(a2n: Int, a: Int): Boolean = {
        val q = a2n / a
        val m = a2n % a
        a2n != 0 && a2n > a && m == 0 && (q & (q - 1)) == 0
    }


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
    * @io input     w.en    : Write Enable.
    * @io input     w.strb  : Write Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     w.index : Write Index, one-hot addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io input     w.data  : Write Data.
    * @io input     w.mask  : Write Mask, comes from BE.
    * 
    * @io input     r.en    : Read Enable.
    * @io input     r.strb  : Read Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     r.index : Read index, one-hot addressing partial data of the transaction,
    *                         which comes from DataID.
    * @io output    r.data  : Read Data.
    * 
    * @io output    valid   : Entry Valid.
    */
    class UpstreamPort extends Bundle {
        // write signals
        val w = new Bundle {
            val en              = Input(Bool())
            val strb            = Input(Vec(paramPayloadCapacity, Bool()))
            val index           = Input(Vec(paramUpstreamMaxBeatCount, Bool()))
            val data            = Input(UInt(paramUpstreamDataWidth.W))
            val mask            = Input(UInt(paramUpstreamMaskWidth.W))
        }

        // read signals
        val r = new Bundle {
            val en              = Input(Bool())
            val strb            = Input(Vec(paramPayloadCapacity, Bool()))
            val index           = Input(Vec(paramUpstreamMaxBeatCount, Bool()))
            val data            = Output(UInt(paramUpstreamDataWidth.W))
        }

        // valid signals
        val valid           = Output(Vec(paramPayloadCapacity, Vec(paramUpstreamMaxBeatCount, Bool())))
    }

    /*
    * Port I/O: Downstream (AXI4 domain) 
    * 
    * @io input     w.en    : Write Enable.
    * @io input     w.strb  : Write Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     w.index : Write Index, one-hot addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io input     w.data  : Write Data.
    * 
    * @io input     r.en    : Read Enable.
    * @io input     r.strb  : Read Strobe, one-hot addressing the transaction entry in payload memory,
    *                         which comes from NCB-allocated Transaction ID.
    * @io input     r.index : Read Index, one-hot addressing partial data of the transaction,
    *                         which comes from AXI4 Reading Progress.
    * @io output    r.data  : Read Data.
    * @io output    r.mask  : Read Mask, which comes from BE, goes to WSTRB.
    */
    class DownstreamPort extends Bundle {
        // write signals
        val w   = new Bundle {
            val en              = Input(Bool())
            val strb            = Input(Vec(paramPayloadCapacity, Bool()))
            val index           = Input(Vec(paramDownstreamMaxBeatCount, Bool()))
            val data            = Input(UInt(paramDownstreamDataWidth.W))
        }

        // read signals
        val r   = new Bundle {
            val en              = Input(Bool())
            val strb            = Input(Vec(paramPayloadCapacity, Bool()))
            val index           = Input(Vec(paramDownstreamMaxBeatCount, Bool()))
            val data            = Output(UInt(paramDownstreamDataWidth.W))
            val mask            = Output(UInt(paramDownstreamMaskWidth.W))
        }

        // valid signals
        val valid           = Output(Vec(paramPayloadCapacity, Vec(paramDownstreamMaxBeatCount, Bool())))
    }

    /*
    * Port I/O: Payload Entry Allocation (w/ Debug)
    * 
    * @io input     en      : Allocation Enable.
    * @io input     strb    : Allocation Strobe, one-hot.
    * @io input     upload  : Allocation Direction, 'Upload' when asserted, otherwise 'Download'.
    *                         - Upload   : AXI to CHI
    *                         - Download : CHI to AXI
    * @io input     mask    : Allocation Valid Mask, only masked segments were needed to be read,
    *                         applying to Downstream (AXI to CHI) Valid Registers.
    */
    class AllocatePort extends Bundle {
        val en              = Input(Bool())
        val strb            = Input(Vec(paramPayloadCapacity, Bool()))
        val upload          = Input(Bool())
        val mask            = Input(Vec(paramDownstreamMaxBeatCount, Bool()))
    }

    /*
    * Port I/O: Payload Entry Free (w/ Debug)
    * 
    * @io input     en      : Free Enable.
    * @io input     strb    : Free Strobe, one-hot.
    */
    class FreePort extends Bundle {
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
    protected val regUpstreamValid  = RegInit(Vec(paramPayloadCapacity, Vec(paramUpstreamMaxBeatCount, Bool())), 
        init = VecInit.fill(paramPayloadCapacity, paramUpstreamMaxBeatCount)(false.B))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regUpstreamValid(i) := VecInit.fill(paramUpstreamMaxBeatCount)(false.B)
        }

        (0 until paramUpstreamMaxBeatCount).foreach(j => {
            when (io.upstream.w.en & io.upstream.w.strb(i) & io.upstream.w.index(j)) {
                regUpstreamValid(i)(j) := true.B
            }
        })
    })

    // Status Payload - Downstream (AXI to CHI) Valid Registers
    protected val regDownstreamValid    = RegInit(Vec(paramPayloadCapacity, Vec(paramDownstreamMaxBeatCount, Bool())), 
        init = VecInit.fill(paramPayloadCapacity, paramDownstreamMaxBeatCount)(false.B))

    (0 until paramPayloadCapacity).foreach(i => {

        when (io.allocate.en & io.allocate.strb(i)) {
            regDownstreamValid(i) := VecInit(io.allocate.mask.map(~_))
        }

        (0 until paramDownstreamMaxBeatCount).foreach(j => {
            when (io.downstream.w.en & io.downstream.w.strb(i) & io.downstream.w.index(j)) {
                regDownstreamValid(i)(j) := true.B
            }
        })
    })


    // Data Payload - Data Registers
    protected val regData   = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotDataCount, UInt(paramPayloadSlotDataWidth.W))))

    (0 until paramPayloadCapacity).foreach(i => {

        (0 until paramUpstreamMaxBeatCount).foreach(j => {

            when (io.upstream.w.en & io.upstream.w.strb(i) & io.upstream.w.index(j)) {

                if (paramSlotCatCountUpstream == 1)
                    regData(i)(j)   := io.upstream.w.data
                else
                    (0 until paramSlotCatCountUpstream).foreach(k => {
                        regData(i)(Cat(j.U, k.U(log2Up(paramSlotCatCountUpstream).W))) :=
                            io.upstream.w.data.extract(k, paramPayloadSlotDataWidth)
                    })
            }
        })

        (0 until paramDownstreamMaxBeatCount).foreach(j => {

            when (io.downstream.w.en & io.downstream.w.strb(i) & io.downstream.w.index(j)) {
        
                if (paramSlotCatCountDownstream == 1)
                    regData(i)(j)   := io.downstream.w.data
                else
                    (0 until paramSlotCatCountDownstream).foreach(k => {
                        regData(i)(Cat(j.U, k.U(log2Up(paramSlotCatCountDownstream).W))) :=
                            io.downstream.w.data.extract(k, paramPayloadSlotDataWidth)
                    })
            }
        })
    })

    

    // Data Payload - Mask Registers
    protected val regMask   = Reg(Vec(paramPayloadCapacity, Vec(paramPayloadSlotMaskCount, UInt(paramPayloadSlotMaskWidth.W))))

    (0 until paramPayloadCapacity).foreach(i => {
        (0 until paramUpstreamMaxBeatCount).foreach(j => {

            when (io.upstream.w.en & io.upstream.w.strb(i) & io.upstream.w.index(j)) {

                if (paramSlotCatCountUpstream == 1)
                    regMask(i)(j)   := io.upstream.w.mask
                else
                    (0 until paramSlotCatCountUpstream).foreach(k => {
                        regMask(i)(Cat(j.U, k.U(log2Up(paramSlotCatCountUpstream).W))) :=
                            io.upstream.w.mask.extract(k, paramPayloadSlotMaskWidth)
                    })
            }
        })
    })

    // read concation and connections for payload data and mask registers
    protected val wireDataVecUpstream   = Wire(Vec(paramPayloadCapacity, 
        Vec(paramUpstreamMaxBeatCount, UInt(paramUpstreamDataWidth.W))))

    protected val wireDataVecDownstream = Wire(Vec(paramPayloadCapacity,
        Vec(paramDownstreamMaxBeatCount, UInt(paramDownstreamDataWidth.W))))

    protected val wireMaskVecUpstream   = Wire(Vec(paramPayloadCapacity,
        Vec(paramUpstreamMaxBeatCount, UInt(paramUpstreamMaskWidth.W))))

    protected val wireMaskVecDownstream = Wire(Vec(paramPayloadCapacity,
        Vec(paramDownstreamMaxBeatCount, UInt(paramDownstreamMaskWidth.W))))

    protected val funcCat = (i: Int, j: Int, k: Int, t: (Vec[Vec[UInt]], Vec[Vec[UInt]])) => {
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
    io.upstream.r.data  := ParallelMux(ParallelMux(
        wireDataVecUpstream.zipWithIndex.map(t => (io.upstream.r.strb(t._2), t._1))
    ).zipWithIndex.map(t => (io.upstream.r.index(t._2), t._1)))

    if (paramUpstreamMaxBeatCount == paramDownstreamMaxBeatCount)
    {
        io.upstream.valid   := regDownstreamValid
    }
    else if (paramUpstreamMaxBeatCount > paramDownstreamMaxBeatCount)
    {
        require(isPower2Mult(paramUpstreamMaxBeatCount, paramDownstreamMaxBeatCount),
            s"NCB Internal Error: power2mult fail: ${paramUpstreamMaxBeatCount}, ${paramDownstreamMaxBeatCount}")

        val splitWidth  = paramUpstreamMaxBeatCount / paramDownstreamMaxBeatCount

        io.upstream.valid.zipWithIndex.foreach({ case (out, n) => {
            regDownstreamValid(n).zipWithIndex.foreach({ case (valid, i) => {
                (0 until splitWidth).foreach(j => {
                    out(i * splitWidth + j) := valid
                })
            }})
        }})
    }
    else
    {
        require(isPower2Mult(paramDownstreamMaxBeatCount, paramUpstreamMaxBeatCount),
            s"NCB Internal Error: power2mult fail: ${paramDownstreamMaxBeatCount}, ${paramUpstreamMaxBeatCount}")

        val mergeWidth  = paramDownstreamMaxBeatCount / paramUpstreamMaxBeatCount

        io.upstream.valid.zipWithIndex.foreach({ case (out, n) => {
            out.zipWithIndex.foreach({ case (out, i) => {
                out := VecInit(
                    (0 until mergeWidth).map(j => regDownstreamValid(n)(i * mergeWidth + j))
                ).asUInt.andR
            }})
        }})
    }

    // downstream outputs
    io.downstream.r.data    := ParallelMux(ParallelMux(
        wireDataVecDownstream.zipWithIndex.map(t => (io.downstream.r.strb(t._2), t._1))
    ).zipWithIndex.map(t => (io.downstream.r.index(t._2), t._1)))

    io.downstream.r.mask    := ParallelMux(ParallelMux(
        wireMaskVecDownstream.zipWithIndex.map(t => (io.downstream.r.strb(t._2), t._1))
    ).zipWithIndex.map(t => (io.downstream.r.index(t._2), t._1)))

    if (paramDownstreamMaxBeatCount == paramUpstreamMaxBeatCount)
    {
        io.downstream.valid := regUpstreamValid
    }
    else if (paramDownstreamMaxBeatCount > paramUpstreamMaxBeatCount)
    {
        require(isPower2Mult(paramDownstreamMaxBeatCount, paramUpstreamMaxBeatCount),
            s"NCB Internal Error: power2mult fail: ${paramDownstreamMaxBeatCount}, ${paramUpstreamMaxBeatCount}")

        val splitWidth  = paramDownstreamMaxBeatCount / paramUpstreamMaxBeatCount

        io.downstream.valid.zipWithIndex.foreach({ case (out, n) => {
            regUpstreamValid(n).zipWithIndex.foreach({ case (valid, i) => {
                (0 until splitWidth).foreach(j => {
                    out(i * splitWidth + j) := valid
                })
            }})
        }})
    }
    else
    {
        require(isPower2Mult(paramUpstreamMaxBeatCount, paramDownstreamMaxBeatCount),
            s"NCB Internal Error: power2mult fail: ${paramUpstreamMaxBeatCount}, ${paramDownstreamMaxBeatCount}")

        val mergeWidth  = paramUpstreamMaxBeatCount / paramDownstreamMaxBeatCount

        io.downstream.valid.zipWithIndex.foreach({ case (out, n) => {
            out.zipWithIndex.foreach({ case (out, i) => {
                out := VecInit(
                    (0 until mergeWidth).map(j => regUpstreamValid(n)(i * mergeWidth + j))
                ).asUInt.andR
            }})
        }})
    }


    // Debug Info - Transaction Allocation Table
    protected val regTransactionTable   = RegInit(new Bundle {
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

        when (io.free.strb(i)) {
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
        debug.DoubleFreeOrCorruptionException(i) := io.free.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.DoubleFreeOrCorruptionException(i),
            s"double free or corruption at [${i}]")
    })

    /*
    * @assertion TransactionDualWriteConfliction 
    *   In one transaction, it's not allowed to write payload simultaneously from both
    *   downstream and upstream.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DualWriteConfliction(i) := io.upstream.w.en && io.upstream.w.strb(i) && io.downstream.w.en && io.downstream.w.strb(i)
        assert(!debug.DualWriteConfliction(i),
            s"payload write confliction by downstream and upstream at [${i}]")
    })

    /*
    * @assertion TransactionDualReadConfliction 
    *   In one transaction, it's not allowed to read payload simultaneously from both
    *   downstream and upstream.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DualReadConfliction(i) := io.upstream.r.en && io.upstream.r.strb(i) && io.downstream.r.en && io.downstream.r.strb(i)
        assert(!debug.DualReadConfliction(i),
            s"payload read confliction by downstream and upstream at [${i}]")
    })

    /* 
    * @assertion UpstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamWriteOutOfBound(i) := io.upstream.w.en && io.upstream.w.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.UpstreamWriteOutOfBound(i),
            s"payload upstream write on non-exist transaction at [${i}]")
    })

    /*
    * @assertion UpstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamReadOutOfBound(i) := io.upstream.r.en && io.upstream.r.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.UpstreamReadOutOfBound(i),
            s"payload upstream read on non-exist transaction at [${i}]")
    })

    /*
    * @assertion DownstreamWriteOutOfBound
    *   Payload writes were not allowed on non-allocated payload slots.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamWriteOutOfBound(i) := io.downstream.w.en && io.downstream.w.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.DownstreamWriteOutOfBound(i),
            s"payload downstream write on non-exist transaction at [${i}]")
    })

    /*
    * @assertion DownstreamReadOutOfBound
    *   Payload reads were not allowed on non-allocated payload slots. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamReadOutOfBound(i) := io.downstream.r.en && io.downstream.r.strb(i) && !regTransactionTable.valid(i)
        assert(!debug.DownstreamReadOutOfBound(i),
            s"payload downstream read on non-exist transaction at [${i}]")
    })

    /*
    * @assertion UpstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    *   e.g. CHI Write Transaction => (CHI Upstream) Write-only <-PAYLOAD-> Read-only (Downstream AXI)
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamWriteDirectionConfliction(i) := io.upstream.w.en && io.upstream.w.strb(i) && regTransactionTable.upload(i)
        assert(!debug.UpstreamWriteDirectionConfliction(i),
            s"payload upstream write direction conflict at [${i}]")
    })

    /*
    * @assertion UpstreamReadDirectionConfliction
    *   Payload read direction must be the same as transaction direction. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.UpstreamReadDirectionConfliction(i) := io.upstream.r.en && io.upstream.r.strb(i) && !regTransactionTable.upload(i)
        assert(!debug.UpstreamReadDirectionConfliction(i),
            s"payload upstream read direction conflict at [${i}]")
    })

    /*
    * @assertion DownstreamWriteDirectionConfliction
    *   Payload write direction must be the same as transaction direction.
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamWriteDirectionConfliction(i) := io.downstream.w.en && io.downstream.w.strb(i) && !regTransactionTable.upload(i)
        assert(!debug.DownstreamWriteDirectionConfliction(i),
            s"payload downstream write direction conflict at [${i}]")
    })

    /* 
    * @assertion DownstreamReadDirectionConfliction 
    *   Payload read direction must be the same as transaction direction. 
    */
    (0 until paramPayloadCapacity).foreach(i => {
        debug.DownstreamReadDirectionConfliction(i) := io.downstream.r.en && io.downstream.r.strb(i) && regTransactionTable.upload(i)
        assert(!debug.DownstreamReadDirectionConfliction(i),
            s"payload downstream read direction conflict at [${i}]")
    })
}
