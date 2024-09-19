package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.util.log2Up
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE
import cc.xiangshan.openncb.chi.CHIConstants
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.util.ParallelMux
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


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
            val CHICancelOrAXIBresp = Bool()
        }

        override def ready: Bool = valid & !barrier.asUInt.orR
    }

    class TransactionOpCHIDBIDResp extends Bundle with TraitTransactionOpElement

    class TransactionOpCHICompDBIDResp extends Bundle with TraitTransactionOpElement 

    class TransactionOpCHIReadReceipt extends Bundle with TraitTransactionOpElement {
        val barrier         = new Bundle {
            val AXIARready          = Bool()
        }

        override def ready: Bool = valid & !barrier.asUInt.orR
    }

    class TransactionOpCHICompData extends Bundle with TraitTransactionOpElement

    class TransactionOpAXIWriteAddress extends Bundle with TraitTransactionOpElement {
        val barrier         = new Bundle {
            val CHIWriteBackData    = Bool()
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
        val Device          = Bool()
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
    * Port I/O: Upstream Write Data Update (for RXDAT)
    */
    class UpstreamWriteDataPort extends Bundle {
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
    * Port I/O: Upstream DAT Operand Write (for TXDAT)
    */
    class UpstreamDatOperandWritePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Critical        = Input(Vec(paramUpstreamMaxBeatCount, Bool()))
            val Count           = Input(UInt(paramUpstreamMaxBeatCountWidth.W))
        }
    }

    /*
    * Port I/O: Downstream AW Op Valid Read 
    */
    class DownstreamAWOpValidPort extends Bundle {
        val valid           = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AW Op Point of No Return
    */
    class DownstreamAWOpPoNRPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AW Op Done
    */
    class DownstreamAWOpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AW Info Read 
    */
    class DownstreamAWInfoReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionInfo)
    }

    /*
    * Port I/O: Downstream AW Operand Read 
    */
    class DownstreamAWOperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandAXI)
    }

    /*
    * Port I/O: Downstream W Op Point of No Return
    */
    class DownstreamWOpPoNRPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream W Op Done
    */
    class DownstreamWOpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream W Operand Read 
    */
    class DownstreamWOperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandAXI)
    }

    /*
    * Port I/O: Downstream W Operand Write
    */
    class DownstreamWOperandWritePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Critical        = Input(Vec(paramDownstreamMaxBeatCount, Bool()))
            val Count           = Input(UInt(paramDownstreamMaxBeatCountWidth.W))
        }
    }

    /*
    * Port I/O: Downstream B Op Done 
    */
    class DownstreamBOpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream B Operand Write 
    */
    class DownstreamBOperandWritePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val WriteRespErr    = Input(UInt(paramCHI.rspRespErrWidth.W))
        }
    }

    /*
    * Port I/O: Downstream AR Op Valid 
    */
    class DownstreamAROpValidPort extends Bundle {
        val valid           = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AR Op Point of No Return
    */
    class DownstreamAROpPoNRPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AR Op Done
    */
    class DownstreamAROpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream AR Info Read 
    */
    class DownstreamARInfoReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionInfo)
    }

    /*
    * Port I/O: Downstream AR Operand Read 
    */
    class DownstreamAROperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandAXI)
    }

    /*
    * Port I/O: Downstream R Op Done
    */
    class DownstreamROpDonePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Downstream R Operand Read 
    */
    class DownstreamROperandReadPort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = Output(new TransactionOperandAXI)
    }

    /*
    * Port I/O: Downstream R Operand AXI Write
    */
    class DownstreamROperandAXIWritePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val Critical        = Input(Vec(paramDownstreamMaxBeatCount, Bool()))
            val Count           = Input(UInt(paramDownstreamMaxBeatCountWidth.W))
        }
    }
    
    /*
    * Port I/O: Downstream R Operand CHI Write
    */
    class DownstreamROperandCHIWritePort extends Bundle {
        val strb            = Input(Vec(paramNCB.outstandingDepth, Bool()))
        val bits            = new Bundle {
            val ReadRespErr     = Input(UInt(paramCHI.datRespErrWidth.W))
        }
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
            val query                   = new UpstreamQueryPort
            val cancel                  = new UpstreamCancelPort
            val writeData               = new UpstreamWriteDataPort
        }

        // upstream ports (for TXRSP)
        val upstreamTxRsp           = new Bundle {
            val opValid                 = new UpstreamRspOpValidPort
            val opRead                  = new UpstreamRspOpReadPort
            val opDone                  = new UpstreamRspOpDonePort
            val infoRead                = new UpstreamRspInfoReadPort
            val operandRead             = new UpstreamRspOperandReadPort
        }
        
        // upstream ports (for TXDAT)
        val upstreamTxDat           = new Bundle {
            val opValid                 = new UpstreamDatOpValidPort
            val opRead                  = new UpstreamDatOpReadPort
            val opDone                  = new UpstreamDatOpDonePort
            val infoRead                = new UpstreamDatInfoReadPort
            val operandRead             = new UpstreamDatOperandReadPort
            val operandWrite            = new UpstreamDatOperandWritePort
        }

        // downstream ports (for AXI AW)
        val downstreamAw            = new Bundle {
            val opValid                 = new DownstreamAWOpValidPort
            val opPoNR                  = new DownstreamAWOpPoNRPort
            val opDone                  = new DownstreamAWOpDonePort
            val infoRead                = new DownstreamAWInfoReadPort
            val operandRead             = new DownstreamAWOperandReadPort
        }

        // downstream ports (for AXI W)
        val downstreamW             = new Bundle {
            val opPoNR                  = new DownstreamWOpPoNRPort
            val opDone                  = new DownstreamWOpDonePort
            val operandRead             = new DownstreamWOperandReadPort
            val operandWrite            = new DownstreamWOperandWritePort
        }

        // downstream ports (for AXI B)
        val downstreamB             = new Bundle {
            val opDone                  = new DownstreamBOpDonePort
            val operandWrite            = new DownstreamBOperandWritePort
        }

        // downstream ports (for AXI AR)
        val downstreamAr            = new Bundle {
            val opValid                 = new DownstreamAROpValidPort
            val opPoNR                  = new DownstreamAROpPoNRPort
            val opDone                  = new DownstreamAROpDonePort
            val infoRead                = new DownstreamARInfoReadPort
            val operandRead             = new DownstreamAROperandReadPort
        }

        // downstream ports (for AXI R)
        val downstreamR             = new Bundle {
            val opDone                  = new DownstreamROpDonePort
            val operandRead             = new DownstreamROperandReadPort
            val operandAXIWrite         = new DownstreamROperandAXIWritePort
            val operandCHIWrite         = new DownstreamROperandCHIWritePort
        }
    })


    /*
    * Transaction Queue Registers
    */
    protected val regQueue  = RegInit(init = VecInit(Seq.fill(paramQueueCapacity){
        val resetValue  = Wire(new Bundle {
            val valid       = Output(Bool())
            val bits        = Output(new QueueEntry)
        })

        resetValue.valid    := false.B
        resetValue.bits     := DontCare
        resetValue.bits.op  := 0.U.asTypeOf(chiselTypeOf(resetValue.bits.op))

        resetValue
    }))

    // entry free logic
    protected val logicEntryFree    = regQueue.map({ entry => {
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

    // entry allocate logic
    val logicAllocateOrderOrdinal   = VecInit((0 until paramQueueCapacity).map(i => {
        io.allocate.bits.order.index === i.U
    }))

    regQueue.zipWithIndex.foreach({ case (entry, i) => {

        when (io.allocate.en & io.allocate.strb(i)) {

            val logicOrderHit   = VecInit(logicEntryFree.zipWithIndex.map({ case (free, j) => {
                if (i == j) false.B else free && logicAllocateOrderOrdinal(j)
            }})).asUInt.orR

            entry.valid := true.B
            
            entry.bits.op           := io.allocate.bits.op
            entry.bits.info         := io.allocate.bits.info
            entry.bits.operand      := io.allocate.bits.operand
            entry.bits.order.index  := io.allocate.bits.order.index
            entry.bits.order.valid  := ValidMux(!logicOrderHit, io.allocate.bits.order.valid)
        }
    }})

    // barrier logic: CHI.Op.Comp.CHICancelOrAXIBresp
    regQueue.zipWithIndex.foreach({ case (entry, i) => {

        // on CHI WriteDataCancel
        when (io.upstreamRxDat.cancel.en & io.upstreamRxDat.cancel.strb(i)) {
            // clear CHI domain barriers
            entry.bits.op.chi.Comp.barrier.CHICancelOrAXIBresp  := false.B

            // clear AXI domain write tasks
            entry.bits.op.axi.WriteAddress  .valid := false.B
            entry.bits.op.axi.WriteData     .valid := false.B
            entry.bits.op.axi.WriteResponse .valid := false.B
        }

        // on AXI BRESP
        when (io.downstreamB.opDone.strb(i)) {
            // clear CHI domain barriers
            entry.bits.op.chi.Comp.barrier.CHICancelOrAXIBresp  := false.B
        }
    }})

    // barrier logic: CHI.Op.ReadReceipt.AXIARready
    regQueue.zipWithIndex.foreach({ case (entry, i) => {

        // on AXI ARREADY
        when (io.downstreamAr.opDone.strb(i)) {
            // clear CHI domain barriers
            entry.bits.op.chi.ReadReceipt.barrier.AXIARready    := false.B
        }
    }})

    // barrier logic: AXI.Op.WriteAddress.CHIWriteBackData
    regQueue.zipWithIndex.foreach({ case (entry, i) => {
    
        // on CHI NonCopyBackWrData
        when (io.upstreamRxDat.writeData.en & io.upstreamRxDat.writeData.strb(i)) {
            // clear AXI domain barriers
            entry.bits.op.axi.WriteAddress.barrier.CHIWriteBackData := false.B
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

    // AXI AW op valid output logic
    io.downstreamAw.opValid.valid.zipWithIndex.map({ case (valid, i) => {
        (valid, regQueue(i).bits.op, regQueue(i).bits.order)
    }}).foreach({ case (valid, entry, order) => {
        valid := entry.axi.WriteAddress.ready & !order.valid
    }})

    // AXI AR op valid output logic
    io.downstreamAr.opValid.valid.zipWithIndex.map({ case (valid, i) => {
        (valid, regQueue(i).bits.op, regQueue(i).bits.order)
    }}).foreach({ case (valid, entry, order) => {
        valid := entry.axi.ReadAddress.ready & !order.valid
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

    // AXI AW op PoNR logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamAw.opPoNR.strb(i))
    }}).foreach({ case (entry, strb) => {

        when(strb) {
            entry.bits.op.axi.WriteAddress      .valid := false.B
        }
    }})

    // AXI W op PoNR logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamW.opPoNR.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.op.axi.WriteData         .valid := false.B
        }
    }})

    // AXI AR op PoNR logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamAr.opPoNR.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.op.axi.ReadAddress       .valid := false.B
        }
    }})

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

    // AXI B op done logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamB.opDone.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.op.axi.WriteResponse     .valid := false.B
        }
    }})

    // AXI R op done logic
    regQueue.zipWithIndex.map({ case (entry ,i) => {
        (entry, io.downstreamR.opDone.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.op.axi.ReadData          .valid := false.B
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

    // AXI AW info read logic
    io.downstreamAw.infoRead.bits   := ParallelMux(
        io.downstreamAw.infoRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.info)
        }})
    )

    // AXI AR info read logic
    io.downstreamAr.infoRead.bits   := ParallelMux(
        io.downstreamAr.infoRead.strb.zipWithIndex.map({ case (strb, i) => {
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

    // AXI AW operand read logic
    io.downstreamAw.operandRead.bits    := ParallelMux(
        io.downstreamAw.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.axi)
        }})
    )

    // AXI W operand read logic
    io.downstreamW.operandRead.bits     := ParallelMux(
        io.downstreamW.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.axi)
        }})
    )

    // AXI AR operand read logic
    io.downstreamAr.operandRead.bits    := ParallelMux(
        io.downstreamAr.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.axi)
        }})
    )

    // AXI R operand read logic
    io.downstreamR.operandRead.bits     := ParallelMux(
        io.downstreamR.operandRead.strb.zipWithIndex.map({ case (strb, i) => {
            (strb, regQueue(i).bits.operand.axi)
        }})
    )

    // TXDAT operand write logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.upstreamTxDat.operandWrite.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.operand.chi.Critical := io.upstreamTxDat.operandWrite.bits.Critical
            entry.bits.operand.chi.Count    := io.upstreamTxDat.operandWrite.bits.Count
        }
    }})

    // AXI W operand write logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamW.operandWrite.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.operand.axi.Critical := io.downstreamW.operandWrite.bits.Critical
            entry.bits.operand.axi.Count    := io.downstreamW.operandWrite.bits.Count
        }
    }})

    // AXI B operand write logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamB.operandWrite.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.operand.chi.WriteRespErr := io.downstreamB.operandWrite.bits.WriteRespErr
        }
    }})

    // AXI R operand write logic
    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamR.operandAXIWrite.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.operand.axi.Critical     := io.downstreamR.operandAXIWrite.bits.Critical
            entry.bits.operand.axi.Count        := io.downstreamR.operandAXIWrite.bits.Count
        }
    }})

    regQueue.zipWithIndex.map({ case (entry, i) => {
        (entry, io.downstreamR.operandCHIWrite.strb(i))
    }}).foreach({ case (entry, strb) => {

        when (strb) {
            entry.bits.operand.chi.ReadRespErr  := io.downstreamR.operandCHIWrite.bits.ReadRespErr
        }
    }})
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
        val DanglingAXIWriteResponse        = Output(Vec(paramQueueCapacity, Bool()))
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    /*
    * @assertion DoubleAllocation
    *   One slot in Transaction Queue must only be allocated once util next free.
    */
    (0 until paramQueueCapacity).foreach(i => {
        debug.DoubleAllocation(i) := io.allocate.en && io.allocate.strb(i) && regQueue(i).valid
        assert(!debug.DoubleAllocation(i),
            s"double allocation at [${i}]")
    })

    /*
    * @assertion DanglingAXIWriteResponse
    *   Received Write Response on AXI B channel with no corresponding transaction.
    */
    (0 until paramQueueCapacity).foreach(i => {
        debug.DanglingAXIWriteResponse(i) := io.downstreamB.opDone.strb(i) &&
            (!regQueue(i).valid || !regQueue(i).bits.op.axi.WriteResponse.valid)
        assert(!debug.DanglingAXIWriteResponse(i),
            s"received BRESP for non-exist transaction at [${i}]")
    })
}
