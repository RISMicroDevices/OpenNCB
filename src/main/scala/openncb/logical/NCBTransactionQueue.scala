package cn.rismd.openncb.logical

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.axi.WithAXI4Parameters
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.WithNCBParameters
import chisel3.util.log2Up
import cn.rismd.openncb.chi.CHIConstants
import cn.rismd.openncb.axi.field.AXI4FieldAxBURST
import cn.rismd.openncb.axi.field.AXI4FieldAxSIZE
import cn.rismd.openncb.util.ParallelMux
import cn.rismd.openncb.debug.DebugBundle
import cn.rismd.openncb.debug.DebugSignal
import cn.rismd.openncb.util.ValidMux
import freechips.rocketchip.util.DataToAugmentedData
import freechips.rocketchip.util.SeqToAugmentedSeq


/*
* NCB Transaction Queue. 
*/
object NCBTransactionQueue {

    case class PublicParameters (
    )

    case object PublicParametersKey extends Field[PublicParameters]
}

class NCBTransactionQueue(implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithCHIParameters
                       with WithNCBParameters {

    // public parameters
    val param   = p.lift(NCBTransactionQueue.PublicParametersKey)
        .getOrElse(new NCBTransactionQueue.PublicParameters)
    

    // local parameters
    protected def paramQueueCapacity                = paramNCB.outstandingDepth
    protected def paramQueueAddressWidth            = paramNCB.outstandingIndexWidth

    protected def paramUpstreamMaxBeatCount         = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramCHI.dataWidth
    protected def paramUpstreamMaxBeatCountWidth    = log2Up(paramUpstreamMaxBeatCount)

    protected def paramDownstreamMaxBeatCount       = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramAXI4.dataWidth
    protected def paramDownstreamMaxBeatCountWidth  = log2Up(paramDownstreamMaxBeatCount)


    /* 
    * Bundle definitions
    */
    trait TraitTransactionOpElement {
        val valid           = Bool()

        def ready: Bool = valid
    }

    class TransactionOpCHIComp extends Bundle with TraitTransactionOpElement {
        val barrier         = new Bundle {
            val CHICancelOrAXIBresp = Input(Bool())
        }

        override def ready: Bool = valid & !barrier.asUInt.orR
    }

    class TransactionOpCHIDBIDResp extends Bundle with TraitTransactionOpElement

    class TransactionOpCHICompDBIDResp extends Bundle with TraitTransactionOpElement 

    class TransactionOpCHIReadReceipt extends Bundle with TraitTransactionOpElement {
        val barrier         = new Bundle {
            val AXIARready      = Bool()
        }

        override def ready: Bool = valid & !barrier.asUInt.orR
    }

    class TransactionOpCHICompData extends Bundle with TraitTransactionOpElement

    class TransactionOpAXIWriteAddress extends Bundle with TraitTransactionOpElement {
        val barrier         = new Bundle {
            val CHIWriteBackData    = Input(Bool())
        }

        override def ready: Bool = valid & !barrier.asUInt.orR
    }

    class TransactionOpAXIWriteData extends Bundle with TraitTransactionOpElement

    class TransactionOpAXIWriteResponse extends Bundle with TraitTransactionOpElement

    class TransactionOpAXIReadAddress extends Bundle with TraitTransactionOpElement

    class TransactionOpAXIReadData extends Bundle with TraitTransactionOpElement

    class TransactionOp extends Bundle {
        // CHI ops
        val chi             = new Bundle {
            //
            val Comp            = new TransactionOpCHIComp
            val DBIDResp        = new TransactionOpCHIDBIDResp
            val CompDBIDResp    = new TransactionOpCHICompDBIDResp
            val ReadReceipt     = new TransactionOpCHIReadReceipt
            //
            val CompData        = new TransactionOpCHICompData

            //
            def valid           = VecInit(
                getElements.map(_.asInstanceOf[TraitTransactionOpElement].valid)
            ).asUInt.orR
        }

        // AXI ops
        val axi             = new Bundle {
            //
            val WriteAddress    = new TransactionOpAXIWriteAddress
            val WriteData       = new TransactionOpAXIWriteData
            val WriteResponse   = new TransactionOpAXIWriteResponse
            //
            val ReadAddress     = new TransactionOpAXIReadAddress
            val ReadData        = new TransactionOpAXIReadData

            //
            def valid       = VecInit(
                getElements.map(_.asInstanceOf[TraitTransactionOpElement].valid)
            ).asUInt.orR
        }
    }

    //
    class TransactionInfo extends Bundle {
        val QoS             = UInt(paramCHI.reqQoSWidth.W)
        val TgtID           = UInt(paramCHI.reqTgtIDWidth.W)
        val SrcID           = UInt(paramCHI.reqSrcIDWidth.W)
        val TxnID           = UInt(paramCHI.reqTxnIDWidth.W)
        val ReturnNID       = UInt(paramCHI.reqReturnNIDWidth.W)
        val ReturnTxnID     = UInt(paramCHI.reqReturnTxnIDWidth.W)
    }

    //
    class TransactionOperandCHI extends Bundle {
        val Addr            = UInt(paramCHI.reqAddrWidth.W)
        //
        val WriteFull       = Bool()
        val WritePtl        = Bool()
        //
        val ReadRespErr     = UInt(paramCHI.rspRespErrWidth.W)
        val WriteRespErr    = UInt(paramCHI.datRespErrWidth.W)
        //
        val Critical        = Vec(paramUpstreamMaxBeatCount, Bool())
        val Count           = UInt(paramUpstreamMaxBeatCountWidth.W)
    }

    class TransactionOperandAXI extends Bundle {
        val Addr            = UInt(paramAXI4.addrWidth.W)
        val Burst           = UInt(AXI4FieldAxBURST.width.W)
        val Size            = UInt(AXI4FieldAxSIZE.width.W)
        val Len             = UInt(8.W)
        //
        val Critical        = Vec(paramDownstreamMaxBeatCount, Bool())
        val Count           = UInt(paramDownstreamMaxBeatCountWidth.W)
    }

    class TransactionOperand extends Bundle {
        // CHI operands
        val chi             = new TransactionOperandCHI

        // AXI operands
        val axi             = new TransactionOperandAXI
    }

    //
    class TransactionOrder extends Bundle {
        val valid           = Bool()
        val index           = UInt(paramQueueAddressWidth.W)
    }
    /**/

    //
    class QueueEntry extends Bundle {
        // operation bit entires
        val op              = new TransactionOp

        // transaction info entries
        val info            = new TransactionInfo

        // transaction operand entries
        val operand         = new TransactionOperand

        // transaction order maintainence entries
        val order           = new TransactionOrder
    }


    /*
    * Port I/O: Transaction Allocation
    */
    class AllocatePort extends Bundle {
        // allocation enable
        val en              = Input(Bool())

        // allocation target strobe
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))

        // allocation transaction bits
        val bits            = new Bundle {
            // operation bits
            val op              = Input(new TransactionOp)

            // transaction infos
            val info            = Input(new TransactionInfo)

            // transaction operands
            val operand         = Input(new TransactionOperand)

            // transaction order maintainence
            val order           = Input(new TransactionOrder)
        }
    }

    /*
    * Port I/O: Transaction Free 
    */
    class FreePort extends Bundle {
        // free strobe
        val strb            = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Upstream Query (for RXDAT)
    */
    class UpstreamQueryPort extends Bundle {
        val en              = Input(Bool())
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val result          = new Bundle {
            val valid           = Output(Bool())
            val WriteFull       = Output(Bool())
            val WritePtl        = Output(Bool())
            val ReadRespErr     = Output(UInt(paramCHI.rspRespErrWidth.W))
            val WriteRespErr    = Output(UInt(paramCHI.datRespErrWidth.W))
        }
    }

    /*
    * Port I/O: Upstream Cancel Update (for RXDAT)
    */
    class UpstreamCancelPort extends Bundle {
        val en              = Input(Bool())
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Upstream RSP Op Valid (for TXRSP)
    */
    class UpstreamRspOpValidPort extends Bundle {
        val valid           = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Upstream RSP Op Read (for TXRSP) 
    */
    class UpstreamRspOpReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Comp            = Output(Bool())
            val DBIDResp        = Output(Bool())
            val CompDBIDResp    = Output(Bool())
            val ReadReceipt     = Output(Bool())
        }
    }

    /*
    * Port I/O: Upstream RSP Op Done (for TXRSP)
    */
    class UpstreamRspOpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Comp            = Input(Bool())
            val DBIDResp        = Input(Bool())
            val CompDBIDResp    = Input(Bool())
            val ReadReceipt     = Input(Bool())
        }
    }

    /* 
    * Port I/O: Upstream RSP Info Read (for TXRSP)
    */
    class UpstreamRspInfoReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionInfo)
    }

    /*
    * Port I/O: Upstream RSP Operand Read (for TXRSP) 
    */
    class UpstreamRspOperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandCHI)
    }

    /*
    * Port I/O: Upstream DAT Op Valid (for TXDAT)
    */
    class UpstreamDatOpValidPort extends Bundle {
        val valid           = Output(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Critical        = Output(Vec(paramNCB.outstandingDepth, Vec(paramUpstreamMaxBeatCount, Bool())))
        }
    }

    /*
    * Port I/O: Upstream DAT Op Read (for TXDAT) 
    */
    class UpstreamDatOpReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val CompData        = new Bundle {
                val valid           = Output(Bool())
            }
        }
    }

    /* 
    * Port I/O: Upstream DAT Op Done (for TXDAT)
    */
    class UpstreamDatOpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val CompData        = Input(Bool())
        }
    }

    /*
    * Port I/O: Upstream DAT Info Read (for TXDAT)
    */
    class UpstreamDatInfoReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionInfo)
    }

    /*
    * Port I/O: Upstream DAT Operand Read (for TXDAT) 
    */
    class UpstreamDatOperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandCHI)
    }


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // free output port
        val free                    = new FreePort

        // allocate port (for RXREQ)
        val allocate                = new AllocatePort

        // upstream ports (for RXDAT)
        val upstreamRxDat           = new Bundle {
            // query port
            val query                   = new UpstreamQueryPort

            // cancel port
            val cancel                  = new UpstreamCancelPort
        }

        // upstream ports (for TXRSP)
        val upstreamTxRsp           = new Bundle {
            // op valid port
            val opValid                 = new UpstreamRspOpValidPort

            // op read port
            val opRead                  = new UpstreamRspOpReadPort

            // op done port
            val opDone                  = new UpstreamRspOpDonePort

            // info read port
            val infoRead                = new UpstreamRspInfoReadPort

            // operand read port
            val operandRead             = new UpstreamRspOperandReadPort
        }
        
        // upstream ports (for TXDAT)
        val upstreamTxDat           = new Bundle {
            // op valid port
            val opValid                 = new UpstreamDatOpValidPort

            // op read port
            val opRead                  = new UpstreamDatOpReadPort

            // op done port
            val opDone                  = new UpstreamDatOpDonePort

            // info read port
            val infoRead                = new UpstreamDatInfoReadPort

            // operand read port
            val operandRead             = new UpstreamDatOperandReadPort
        }
    })


    /*
    * Transaction Queue Registers
    */
    val regQueue    = RegInit(init = VecInit(Seq.fill(paramQueueCapacity){
        val resetValue  = Wire(new Bundle {
            val valid       = Output(Bool())
            val bits        = Output(new QueueEntry)
        })

        resetValue.valid    := false.B
        resetValue.bits     := DontCare

        resetValue
    }))

    // entry allocate logic
    regQueue.zipWithIndex.foreach({ case (entry, i) => {

        when (io.allocate.en & io.allocate.strb(i)) {

            entry.valid := true.B
            
            entry.bits.op       := io.allocate.bits.op
            entry.bits.info     := io.allocate.bits.info
            entry.bits.operand  := io.allocate.bits.operand
            entry.bits.order    := io.allocate.bits.order
        }
    }})

    // entry free logic
    val logicEntryFree  = regQueue.map({ entry => {
        entry.valid & !entry.bits.op.chi.valid & !entry.bits.op.axi.valid
    }})

    io.free.strb    := logicEntryFree
    
    logicEntryFree.zipWithIndex.foreach({ case (free, i) => {
        // free self
        when (free) {
            regQueue(i).valid   := false.B
        }
    }})

    regQueue.zipWithIndex.foreach({ case (entry, i) => {
        // free order relation
        val logicOrderHit = VecInit(logicEntryFree.zipWithIndex.map({ case (free, j) => {
            if (i == j) false.B else free && entry.bits.order.index === j.U
        }})).asUInt.orR

        when(logicOrderHit) {
            entry.bits.order.valid  := false.B
        }
    }})

    // barrier logic: Op.Comp.CHICancelOrAXIBresp
    regQueue.zipWithIndex.foreach({ case (entry, i) => {

        when (io.upstreamRxDat.cancel.en & io.upstreamRxDat.cancel.strb(i)) {
            entry.bits.op.chi.Comp.barrier.CHICancelOrAXIBresp  := false.B
        }
    }})

    // TXRSP op valid output logic
    io.upstreamTxRsp.opValid.valid.zipWithIndex.map({ case (valid, i) => {
        (valid, i, regQueue(i).bits.op, regQueue(i).bits.order)
    }}).foreach({ case (valid, i, entry, order) => {
        valid := VecInit(Seq(
            entry.chi.Comp          .ready,
            entry.chi.DBIDResp      .ready,
            entry.chi.CompDBIDResp  .ready,
            entry.chi.ReadReceipt   .ready
        )).asUInt.orR & !order.valid
    }})

    // TXDAT op valid output logic
    io.upstreamTxDat.opValid.valid.zipWithIndex.map({ case (valid, i) => {
        (valid, i, regQueue(i).bits.op, regQueue(i).bits.order)
    }}).foreach({ case (valid, i, entry, order) => {
        valid := VecInit(Seq(
            entry.chi.CompData      .valid
        )).asUInt.orR & !order.valid
    }})

    io.upstreamTxDat.opValid.bits.Critical.zipWithIndex.map({ case (critical, i) => {
        (critical, regQueue(i).bits.operand.chi.Critical)
    }}).foreach({ case (critical, entry) => {
        critical := entry
    }})

    // TXRSP op read logic
    io.upstreamTxRsp.opRead.bits := ParallelMux(
        io.upstreamTxRsp.opRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.op)
        }}).map({ case (strb, op) => {

            val valid   = Wire(chiselTypeOf(io.upstreamTxRsp.opRead.bits))

            valid.Comp          := op.chi.Comp          .ready
            valid.DBIDResp      := op.chi.DBIDResp      .ready
            valid.CompDBIDResp  := op.chi.CompDBIDResp  .ready
            valid.ReadReceipt   := op.chi.ReadReceipt   .ready

            (strb, valid)
        }})
    )

    // TXDAT op read logic
    io.upstreamTxDat.opRead.bits := ParallelMux(
        io.upstreamTxDat.opRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.op)
        }}).map({ case (strb, entry) => {

            val op  = Wire(chiselTypeOf(io.upstreamTxDat.opRead.bits))

            op.CompData.valid       := entry.chi.CompData.valid

            (strb, op)
        }})
    )

    // TXRSP op done logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.upstreamTxRsp.opDone.strb(i), io.upstreamTxRsp.opDone.bits)
    }}).foreach({ case (entry, strb, bits) => {

        when (strb) {

            when (bits.Comp) {
                entry.bits.op.chi.Comp          .valid := false.B
            }

            when (bits.DBIDResp) {
                entry.bits.op.chi.DBIDResp      .valid := false.B
            }

            when (bits.CompDBIDResp) {
                entry.bits.op.chi.CompDBIDResp  .valid := false.B
            }

            when (bits.ReadReceipt) {
                entry.bits.op.chi.ReadReceipt   .valid := false.B
            }
        }        
    }})

    // TXDAT op done logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.upstreamTxDat.opDone.strb(i), io.upstreamTxDat.opDone.bits)
    }}).foreach({ case (entry, strb, bits) => {

        when (strb) {

            when (bits.CompData & entry.bits.operand.chi.Count === 0.U) {
                entry.bits.op.chi.CompData      .valid := false.B
            }
        }
    }})

    // TXRSP info read logic
    io.upstreamTxRsp.infoRead.bits  := ParallelMux(
        io.upstreamTxRsp.infoRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.info)
        }})
    )

    // TXDAT info read logic
    io.upstreamTxDat.infoRead.bits  := ParallelMux(
        io.upstreamTxDat.infoRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.info)
        }})
    )

    // TXRSP operand read logic
    io.upstreamTxRsp.operandRead.bits   := ParallelMux(
        io.upstreamTxDat.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.chi)
        }})
    )
    
    // TXDAT operand read logic
    io.upstreamTxDat.operandRead.bits   := ParallelMux(
        io.upstreamTxDat.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.chi)
        }})
    )
    /**/


    // upstream query logic
    io.upstreamRxDat.query.result := ValidMux(io.upstreamRxDat.query.en, 
        ParallelMux(io.upstreamRxDat.query.strb.zip(regQueue.map(entry => {
            val result = Wire(Output(chiselTypeOf(io.upstreamRxDat.query.result)))
            result.valid        := entry.valid
            result.WriteFull    := entry.bits.operand.chi.WriteFull
            result.WritePtl     := entry.bits.operand.chi.WritePtl
            result.ReadRespErr  := entry.bits.operand.chi.ReadRespErr
            result.WriteRespErr := entry.bits.operand.chi.WriteRespErr
            result
        })))
    )


    // assertions & debugs
    /*
    * Port I/O: Debug
    */
    class DebugPort extends DebugBundle {
        val DoubleAllocation                = Output(Vec(paramQueueCapacity, Bool()))
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion DoubleAllocation
    *  One slot in Transaction Queue must only be allocated once util next free.
    */
    (0 until paramQueueCapacity).foreach(i => {
        debug.DoubleAllocation(i) := io.allocate.en && io.allocate.strb(i) && regQueue(i).valid
        assert(!debug.DoubleAllocation(i),
            s"double allocation at [${i}]")
    })
}
