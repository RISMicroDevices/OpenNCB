package cn.rismd.openncb.logical

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cn.rismd.openncb.chi.WithCHIParameters
import cn.rismd.openncb.WithNCBParameters
import cn.rismd.openncb.chi.channel.CHIChannelRXREQ
import cn.rismd.openncb.logical.chi.CHILinkCreditManagerRX
import cn.rismd.openncb.logical.chi.CHILinkActiveManagerRX
import cn.rismd.openncb.chi.opcode.CHISNFOpcodesREQ
import cn.rismd.openncb.debug.CompanionConnection
import cn.rismd.openncb.debug.DebugBundle
import cn.rismd.openncb.debug.DebugSignal
import cn.rismd.openncb.chi.field.CHIFieldSize
import cn.rismd.openncb.chi.field.CHIFieldOrder
import cn.rismd.openncb.chi.field.CHIFieldMemAttr
import cn.rismd.openncb.chi.field.EnumCHIFieldOrder
import cn.rismd.openncb.util.XZBarrier
import cn.rismd.openncb.axi.field.AXI4FieldAxSIZE
import cn.rismd.openncb.axi.field.AXI4FieldAxBURST
import cn.rismd.openncb.axi.WithAXI4Parameters
import cn.rismd.openncb.chi.CHIConstants
import cn.rismd.openncb.EnumAXIMasterOrder
import cn.rismd.openncb.chi.field.CHIFieldRespErr


/* 
* NCB Upstream Port RXREQ
*/
object NCBUpstreamRXREQ {

    case class PublicParameters (
        /*
        * enableLikelySharedCheck: Enable assertion of 'LikelyShared = 0'. 
        * 
        * * By default, {@code enableLikelySharedCheck} is set to {@value true}.
        */
        enableLikelySharedCheck             : Boolean           = true,

        /*
        * enableFirstAllowRetryCheck: Enable assertion of 'AllowRetry = 1' for first
        *                             transaction flit except PrefetchTgt.
        * 
        * * By default, {@code enableFirstAllowRetryCheck} is set to {@value true}.
        */
        enableFirstAllowRetryCheck          : Boolean           = true,

        /*
        * enablePrefetchTgtAllowRetryCheck: Enable assertion of 'AllowRetry = 0' for
        *                                   PrefetchTgt. 
        * 
        * * By default, {@code enablePrefetchTgtAllowRetryCheck} is set to {@value true}.
        */
        enablePrefetchTgtAllowRetryCheck    : Boolean           = true,

        /*
        * enablePCrdTypeCheck: Enable assertion of 'PCrdType = 0' when 'AllowRetry = 1'.
        * 
        * * By default, {@code enablePCrdTypeCheck} is set to {@value true}. 
        */
        enablePCrdTypeCheck                 : Boolean           = true
    )

    case object PublicParametersKey extends Field[PublicParameters]

    // companion connections
    @CompanionConnection
    def apply(uLinkActiveManager    : CHILinkActiveManagerRX,
              uTransactionFreeList  : NCBTransactionFreeList,
              uTransactionAgeMatrix : NCBTransactionAgeMatrix,
              uOrderAddressCAM      : NCBOrderAddressCAM,
              uOrderRequestCAM      : NCBOrderRequestCAM,
              uTransactionQueue     : NCBTransactionQueue,
              uTransactionPayload   : NCBTransactionPayload)
             (implicit p: Parameters) = {
        val u   = Module(new NCBUpstreamRXREQ(uLinkActiveManager,
                                              uTransactionFreeList,
                                              uTransactionAgeMatrix,
                                              uOrderAddressCAM,
                                              uOrderRequestCAM,
                                              uTransactionQueue,
                                              uTransactionPayload))

        // companion connection: LinkActiveManager
        u.io.linkState <> uLinkActiveManager.io.linkState

        // companion connection: NCBTransactionFreeList
        u.io.freeListAllocate   <> uTransactionFreeList.io.allocate
        u.io.freeListFree       <> uTransactionFreeList.io.free

        // companion connection: NCBTransactionAgeMatrix
        u.io.ageUpdate <> uTransactionAgeMatrix.io.update

        // companion connection: NCBOrderAddressCAM
        u.io.addressOrderAllocate   <> uOrderAddressCAM.io.allocate
        u.io.addressOrderQuery      <> uOrderAddressCAM.io.query
        u.io.addressOrderFree       <> uOrderAddressCAM.io.free

        // companion connection: NCBOrderRequestCAM
        u.io.requestOrderAllocate   <> uOrderRequestCAM.io.allocate
        u.io.requestOrderQuery      <> uOrderRequestCAM.io.query
        u.io.requestOrderFree       <> uOrderRequestCAM.io.free

        // companion connection: NCBTransactionQueue
        u.io.queueAllocate  <> uTransactionQueue.io.allocate
        u.io.queueFree      <> uTransactionQueue.io.free
        
        // companion connection: NCBTransactionPayload
        u.io.payloadAllocate    <> uTransactionPayload.io.allocate
        u.io.payloadFree        <> uTransactionPayload.io.free

        u
    }
}

/*
* @param uLinkActiveManager     Module instance of CHILinkActiveManagerRX
* @param uTransactionFreeList   Module instance of NCBTransactionFreeList
* @param uTransactionQueue      Module instance of NCBTransactionQueue
* @param uTransactionPayload    Module instance of NCBTransactionPayload
*/
class NCBUpstreamRXREQ(val uLinkActiveManager       : CHILinkActiveManagerRX,
                       val uTransactionFreeList     : NCBTransactionFreeList,
                       val uTransactionAgeMatrix    : NCBTransactionAgeMatrix,
                       val uOrderAddressCAM         : NCBOrderAddressCAM,
                       val uOrderRequestCAM         : NCBOrderRequestCAM,
                       val uTransactionQueue        : NCBTransactionQueue,
                       val uTransactionPayload      : NCBTransactionPayload)
        (implicit val p: Parameters)
        extends Module with WithAXI4Parameters
                       with WithCHIParameters
                       with WithNCBParameters 
                       with CHISNFOpcodesREQ {
    
    //
    def unsupportedCHIDataWidth(width: Int) =
        throw new IllegalArgumentException(s"unsupported CHI data width: ${width}")


    // public parameters
    val param   = p.lift(NCBUpstreamRXREQ.PublicParametersKey)
        .getOrElse(new NCBUpstreamRXREQ.PublicParameters)

    // local parameters
    protected def paramUpstreamMaxBeatCount         = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramCHI.dataWidth

    protected def paramUpstreamMaxBeatCountWidth    = log2Up(paramUpstreamMaxBeatCount)

    protected def paramDownstreamMaxBeatCount       = CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH / paramAXI4.dataWidth

    protected def paramDownstreamMaxBeatCountWidth  = log2Up(paramDownstreamMaxBeatCount)


    //
    def unknownAXIMasterOrder =
        throw new IllegalArgumentException(
            s"NCB Internal Error: unknown axiMasterOrder configuration: ${paramNCB.axiMasterOrder}")


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // upstream RX REQ port (CHI domain)
        val rxreq                   = CHIChannelRXREQ()

        // from LinkActiveManagerRX
        @CompanionConnection
        val linkState               = Flipped(chiselTypeOf(uLinkActiveManager.io.linkState))

        // Allocate Port to NCBTransactionFreeList
        @CompanionConnection
        val freeListAllocate        = Flipped(chiselTypeOf(uTransactionFreeList.io.allocate))

        // Free Port to NCBTransactionFreeList
        @CompanionConnection
        val freeListFree            = Flipped(chiselTypeOf(uTransactionFreeList.io.free))

        // Update Port to NCBTransactionAgeMatrix
        @CompanionConnection
        val ageUpdate               = Flipped(chiselTypeOf(uTransactionAgeMatrix.io.update))

        // Allocate Port to NCBOrderAddressCAM
        @CompanionConnection
        val addressOrderAllocate    = Flipped(chiselTypeOf(uOrderAddressCAM.io.allocate))

        // Query Port to NCBOrderAddressCAM
        @CompanionConnection
        val addressOrderQuery       = Flipped(chiselTypeOf(uOrderAddressCAM.io.query))

        // Free Port to NCBOrderAddressCAM
        @CompanionConnection
        val addressOrderFree        = Flipped(chiselTypeOf(uOrderAddressCAM.io.free))

        // Allocate Port to NCBOrderRequestCAM
        @CompanionConnection
        val requestOrderAllocate    = Flipped(chiselTypeOf(uOrderRequestCAM.io.allocate))

        // Query Port to NCBOrderRequestCAM
        @CompanionConnection
        val requestOrderQuery       = Flipped(chiselTypeOf(uOrderRequestCAM.io.query))

        // Free Port to NCBOrderRequestCAM
        @CompanionConnection
        val requestOrderFree        = Flipped(chiselTypeOf(uOrderRequestCAM.io.free))

        // Allocate Port to NCBTransactionPayload
        @CompanionConnection
        val payloadAllocate         = Flipped(chiselTypeOf(uTransactionPayload.io.allocate))

        // Free Port to NCBTransactionPayload
        @CompanionConnection
        val payloadFree             = Flipped(chiselTypeOf(uTransactionPayload.io.free))

        // Allocate Port to NCBTransactionQueue
        @CompanionConnection
        val queueAllocate           = Flipped(chiselTypeOf(uTransactionQueue.io.allocate))

        // Free Port from NCBTransactionQueue
        @CompanionConnection
        val queueFree               = Flipped(chiselTypeOf(uTransactionQueue.io.free))
    })


    // Upstream RXREQ Input Register
    protected val regRXREQ  = RegInit({
        val resetValue = Wire(io.rxreq.undirectedChiselType)
        resetValue.lcrdv    := false.B
        resetValue.flitpend := false.B
        resetValue.flitv    := false.B
        resetValue.flit     := DontCare
        resetValue
    })

    io.rxreq.lcrdv      := regRXREQ.lcrdv
    regRXREQ.flitpend   := io.rxreq.flitpend
    regRXREQ.flitv      := io.rxreq.flitv

    when (io.rxreq.flitv) {
        regRXREQ.flit := io.rxreq.flit
    }


    // Module: Link Credit Manager
    protected val uLinkCredit   = Module(new CHILinkCreditManagerRX(
        paramMaxCount           = paramNCB.outstandingDepth,
        paramInitialCount       = paramNCB.outstandingDepth,
        paramCycleBeforeSend    = 0,                            // TODO: parameterize on demand
        paramEnableMonitor      = true
    ))

    regRXREQ.lcrdv              := uLinkCredit.io.lcrdv

    uLinkCredit.io.linkState    := io.linkState

    // on Transaction Queue entry free
    val uLinkCreditProvideBuffer = uLinkCredit.attachLinkCreditProvideBuffer()
    uLinkCreditProvideBuffer.io.linkCreditProvide := {
        
        val regFreePopCount     = RegInit(
            init = 0.U(log2Up(CHIConstants.CHI_MAX_REASONABLE_LINK_CREDIT_COUNT).W))
        
        val logicFreePopCount   = PopCount(io.queueFree.strb)
        val logicFreeCredit     = regFreePopCount =/= 0.U

        regFreePopCount := regFreePopCount + logicFreePopCount - Mux(logicFreeCredit, 1.U, 0.U)

        assert (!(Cat(0.U, regFreePopCount) + logicFreePopCount)(regFreePopCount.getWidth),
            "NCB Internal Error: transaction queue free strobe overflow")

        logicFreeCredit
    }

    io.freeListFree.strb        := io.queueFree.strb

    io.addressOrderFree.strb    := io.queueFree.strb
    io.requestOrderFree.strb    := io.queueFree.strb

    io.payloadFree.strb         := io.queueFree.strb


    // Module: CHI Opcode Decoder
    protected val uDecoder  = Module(new Decoder(Seq(
    //  ========================
        ReqLCrdReturn,
    //  ------------------------
        ReadNoSnp,
        WriteNoSnpFull,
        WriteNoSnpPtl,
    //  ------------------------
        CleanShared,
        CleanSharedPersist,
        CleanInvalid,
        MakeInvalid,
    //  ------------------------
        PrefetchTgt
    //  ========================
    ), true))

    uDecoder.io.valid   := regRXREQ.flitv
    uDecoder.io.opcode  := regRXREQ.flit.Opcode.get

    val logicTransactionRead    = uDecoder.is(ReadNoSnp)
    val logicTransactionWrite   = uDecoder.is(WriteNoSnpPtl, WriteNoSnpFull)
    val logicLCrdReturn         = uDecoder.is(ReqLCrdReturn)


    // link credit consume on flit valid
    uLinkCredit.io.monitorCreditConsume := regRXREQ.flitv & !logicLCrdReturn

    // link credit return by ReqLCrdReturn
    uLinkCredit.io.monitorCreditReturn  := logicLCrdReturn


    // transaction allocation
    io.freeListAllocate.en := regRXREQ.flitv & !logicLCrdReturn


    // payload allocation
    io.payloadAllocate.en       := regRXREQ.flitv & !logicLCrdReturn
    io.payloadAllocate.strb     := io.freeListAllocate.strb
    io.payloadAllocate.upload   := logicTransactionRead

    // age matrix update
    io.ageUpdate.en     := regRXREQ.flitv & !logicLCrdReturn
    io.ageUpdate.strb   := io.freeListAllocate.strb


    // address order allocation
    def funcGetOrderMask(size: UInt): UInt = {
        MuxLookup(size, 0.U)(Seq(
            CHIFieldSize.Size1B .U  -> "b111111".U(6.W),
            CHIFieldSize.Size2B .U  -> "b111110".U(6.W),
            CHIFieldSize.Size4B .U  -> "b111100".U(6.W),
            CHIFieldSize.Size8B .U  -> "b111000".U(6.W),
            CHIFieldSize.Size16B.U  -> "b110000".U(6.W),
            CHIFieldSize.Size32B.U  -> "b100000".U(6.W),
            CHIFieldSize.Size64B.U  -> "b000000".U(6.W)
        ))
    }

    paramNCB.axiMasterOrder match {

        case EnumAXIMasterOrder.Request => {
            io.addressOrderAllocate := DontCare
        }

        case EnumAXIMasterOrder.Address => {
            io.addressOrderAllocate.en      := logicTransactionWrite || logicTransactionRead
            io.addressOrderAllocate.strb    := io.freeListAllocate.strb
            io.addressOrderAllocate.addr    := regRXREQ.flit.Addr.get
            io.addressOrderAllocate.mask    := funcGetOrderMask(regRXREQ.flit.Size.get)
        }

        case EnumAXIMasterOrder.Write => {
            io.addressOrderAllocate := DontCare
        }

        case EnumAXIMasterOrder.WriteAddress => {
            io.addressOrderAllocate.en      := logicTransactionWrite
            io.addressOrderAllocate.strb    := io.freeListAllocate.strb
            io.addressOrderAllocate.addr    := regRXREQ.flit.Addr.get
            io.addressOrderAllocate.mask    := funcGetOrderMask(regRXREQ.flit.Size.get)
        }

        case EnumAXIMasterOrder.None => {
            io.addressOrderAllocate := DontCare
        }

        case _ => unknownAXIMasterOrder
    }

    // request order allocation
    paramNCB.axiMasterOrder match {

        case EnumAXIMasterOrder.Request => {
            io.requestOrderAllocate.en      := logicTransactionWrite || logicTransactionRead
            io.requestOrderAllocate.strb    := io.freeListAllocate.strb
        }

        case EnumAXIMasterOrder.Address => {
            io.requestOrderAllocate := DontCare
        }

        case EnumAXIMasterOrder.Write => {
            io.requestOrderAllocate.en      := logicTransactionWrite
            io.requestOrderAllocate.strb    := io.freeListAllocate.strb
        }

        case EnumAXIMasterOrder.WriteAddress => {
            io.requestOrderAllocate := DontCare
        }

        case EnumAXIMasterOrder.None => {
            io.requestOrderAllocate := DontCare
        }

        case _ => unknownAXIMasterOrder
    }


    /* 
    * transaction allocation
    */
    io.queueAllocate.en     := regRXREQ.flitv & !logicLCrdReturn
    io.queueAllocate.strb   := io.freeListAllocate.strb

    // allocate CHI operation 'Comp'
    io.queueAllocate.bits.op.chi.Comp.valid := uDecoder.is(
    //  ========================
        CleanShared,
        CleanSharedPersist,
        CleanInvalid,
        MakeInvalid,
    //  ------------------------
        WriteNoSnpFull,
        WriteNoSnpPtl
    //  ========================
    ) && !io.queueAllocate.bits.op.chi.CompDBIDResp.valid

    io.queueAllocate.bits.op.chi.Comp.barrier.CHICancelOrAXIBresp   := (
        logicTransactionWrite
    ) && !CHIFieldMemAttr.EWA.is(regRXREQ) && (
        (paramNCB.axiMasterOrder == EnumAXIMasterOrder.None ).B ||
        (paramNCB.writeNoError   == false                   ).B
    )

    // allocate CHI operation 'DBIDResp'
    io.queueAllocate.bits.op.chi.DBIDResp.valid := (
        logicTransactionWrite
    ) && !io.queueAllocate.bits.op.chi.CompDBIDResp.valid

    // allocate CHI operation 'CompDBIDResp'
    io.queueAllocate.bits.op.chi.CompDBIDResp.valid := (
        logicTransactionWrite
    ) && (paramNCB.writeNoError            == true ).B &&
         (paramNCB.writeCompPreferSeperate == false).B && (
        (CHIFieldMemAttr.EWA.is(regRXREQ)) || (paramNCB.axiMasterOrder != EnumAXIMasterOrder.None).B
    )

    // allocate CHI operation 'ReadReceipt'
    io.queueAllocate.bits.op.chi.ReadReceipt.valid  := (
        logicTransactionRead
    ) && CHIFieldOrder.RequestAccepted.is(regRXREQ)

    io.queueAllocate.bits.op.chi.ReadReceipt.barrier.AXIARready := (
        paramNCB.readReceiptAfterAcception.B
    )

    // allocate CHI operation 'CompData'
    io.queueAllocate.bits.op.chi.CompData.valid := (
        logicTransactionRead
    )

    // allocate AXI operation 'WriteAddress'
    io.queueAllocate.bits.op.axi.WriteAddress.valid     := logicTransactionWrite
    io.queueAllocate.bits.op.axi.WriteAddress.barrier.CHIWriteBackData := {
        (paramNCB.writeNoError == true).B && !uDecoder.is(WriteNoSnpFull)
    }

    // allocate AXI operation 'WriteData'
    io.queueAllocate.bits.op.axi.WriteData      .valid  := logicTransactionWrite

    // allocate AXI operation 'WriteResponse'
    io.queueAllocate.bits.op.axi.WriteResponse  .valid  := logicTransactionWrite

    // allocate AXI operation 'ReadAddress'
    io.queueAllocate.bits.op.axi.ReadAddress    .valid  := logicTransactionRead

    // allocate AXI operation 'ReadData'
    io.queueAllocate.bits.op.axi.ReadData       .valid  := logicTransactionRead

    // order maintainence fields
    io.addressOrderQuery.addr   := regRXREQ.flit.Addr.get
    io.addressOrderQuery.mask   := funcGetOrderMask(regRXREQ.flit.Size.get)

    paramNCB.axiMasterOrder match {

        case EnumAXIMasterOrder.Request => {
            io.queueAllocate.bits.order.valid   := io.requestOrderQuery.resultValid
            io.queueAllocate.bits.order.index   := io.requestOrderQuery.resultIndex
        }

        case EnumAXIMasterOrder.Address => {
            io.queueAllocate.bits.order.valid   := io.addressOrderQuery.resultValid
            io.queueAllocate.bits.order.index   := io.addressOrderQuery.resultIndex
        }

        case EnumAXIMasterOrder.Write => {
            io.queueAllocate.bits.order.valid   := io.requestOrderQuery.resultValid
            io.queueAllocate.bits.order.index   := io.requestOrderQuery.resultIndex
        }

        case EnumAXIMasterOrder.WriteAddress => {
            io.queueAllocate.bits.order.valid   := io.addressOrderQuery.resultValid
            io.queueAllocate.bits.order.index   := io.addressOrderQuery.resultIndex
        }

        case EnumAXIMasterOrder.None => {
            io.queueAllocate.bits.order.valid   := false.B
            io.queueAllocate.bits.order.index   := DontCare
        }

        case _ => unknownAXIMasterOrder
    }

    //
    io.queueAllocate.bits.info.QoS          := regRXREQ.flit.QoS.get
    io.queueAllocate.bits.info.TgtID        := regRXREQ.flit.TgtID.get
    io.queueAllocate.bits.info.SrcID        := regRXREQ.flit.SrcID.get
    io.queueAllocate.bits.info.TxnID        := regRXREQ.flit.TxnID.get
    io.queueAllocate.bits.info.ReturnNID    := regRXREQ.flit.ReturnNID.get
    io.queueAllocate.bits.info.ReturnTxnID  := regRXREQ.flit.ReturnTxnID.get

    // convert to CHI operands
    io.queueAllocate.bits.operand.chi.Addr  := io.rxreq.flit.Addr.get

    io.queueAllocate.bits.operand.chi.WriteFull := uDecoder.is(WriteNoSnpFull)
    io.queueAllocate.bits.operand.chi.WritePtl  := uDecoder.is(WriteNoSnpPtl)

    io.queueAllocate.bits.operand.chi.ReadRespErr   := CHIFieldRespErr.OK.U
    io.queueAllocate.bits.operand.chi.WriteRespErr  := CHIFieldRespErr.OK.U

    io.queueAllocate.bits.operand.chi.Critical  := VecInit({

        if (paramUpstreamMaxBeatCount > 1)
            (0 until paramUpstreamMaxBeatCount).map(i => {
                regRXREQ.flit.Addr.get(5, 6 - paramUpstreamMaxBeatCountWidth) === i.U
            })
        else
            Seq(true.B)
    })

    io.queueAllocate.bits.operand.chi.Count     := {
        if (paramCHI.dataWidth == 128) {
            MuxLookup(regRXREQ.flit.Size.get, 0.U)(Seq(
                CHIFieldSize.Size32B.U  -> 1.U,
                CHIFieldSize.Size64B.U  -> 3.U
            ))

        } else if (paramCHI.dataWidth == 256) {
            MuxLookup(regRXREQ.flit.Size.get, 0.U)(Seq(
                CHIFieldSize.Size64B.U  -> 1.U
            ))

        } else if (paramCHI.dataWidth == 512) {
            0.U

        } else {
            unsupportedCHIDataWidth(paramCHI.dataWidth)
        }
    }


    // convert to AXI operands
    io.queueAllocate.bits.operand.axi.Addr  := io.rxreq.flit.Addr.get

    when (CHIFieldSize.Size1B.is(regRXREQ)) {
        io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size1B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

    }.elsewhen (CHIFieldSize.Size2B.is(regRXREQ)) {
        io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size2B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

    }.elsewhen (CHIFieldSize.Size4B.is(regRXREQ)) {
        io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

    }.elsewhen (CHIFieldSize.Size8B.is(regRXREQ)) {
        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

        } else {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U
        }
    }.elsewhen (CHIFieldSize.Size16B.is(regRXREQ)) {
        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

        } else {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U
        }
    }.elsewhen (CHIFieldSize.Size32B.is(regRXREQ)) {
        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 7.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

        } else if (paramAXI4.dataWidth == 128) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

        } else {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size32B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U
        }
    }.otherwise /*(CHIFieldSize.Size64B.is(regRXREQ))*/ {
        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 15.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 7.U

        } else if (paramAXI4.dataWidth == 128) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

        } else if (paramAXI4.dataWidth == 256) {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size32B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

        } else {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size64B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U
        }
    }

    io.queueAllocate.bits.operand.axi.Critical  := VecInit({

        if (paramDownstreamMaxBeatCount > 1)
            (0 until paramDownstreamMaxBeatCount).map(i => {
                regRXREQ.flit.Addr.get(5, 6 - paramDownstreamMaxBeatCountWidth) === i.U
            })
        else
            Seq(true.B)
    })

    io.queueAllocate.bits.operand.axi.Count     := io.queueAllocate.bits.operand.axi.Len
    /**/
    //


    // assertions & debugs
    /*
    * Port I/O: Debug 
    */
    class DebugPort extends DebugBundle {
        // flat
        val WriteFullWithNarrowSize             = Output(Bool())
        val NonZeroLikelyShared                 = Output(Bool())
        val PrefetchTgtWithNonZeroAllowRetry    = Output(Bool())
        val ZeroFirstAllowRetry                 = Output(Bool())
        val WriteWithIllegalOrder               = Output(Bool())
        val ReadWithIllegalOrder                = Output(Bool())
        val DatalessWithIllegalOrder            = Output(Bool())
        val AllowRetryWithNonZeroPCrdType       = Output(Bool())
        val IllegalMemAttr                      = Output(Bool())
        val NonZeroSnpAttr                      = Output(Bool())
        val NonZeroExcl                         = Output(Bool())
        val NonZeroExpCompAck                   = Output(Bool())
        val IllegalSize                         = Output(Bool())

        // submodule
        val linkCredit                          = chiselTypeOf(uLinkCredit.debug)
        val linkCreditProvide                   = chiselTypeOf(uLinkCreditProvideBuffer.debug)
        val decoder                             = chiselTypeOf(uDecoder.debug)
    }

    @DebugSignal
    val debug   = IO(new DebugPort)

    debug.linkCredit        <> uLinkCredit.debug
    debug.linkCreditProvide <> uLinkCreditProvideBuffer.debug
    debug.decoder           <> uDecoder.debug

    /* 
    * @assertion WriteFullWithNarrowSize
    *   The 'Size' of 'WriteNoSnpFull' was allowed to be full size (64B) only.
    */
    debug.WriteFullWithNarrowSize := XZBarrier(regRXREQ.flitv, uDecoder.is(
                                            WriteNoSnpFull
                                        ) && regRXREQ.flit.Size.get =/= CHIFieldSize.SIZE_64B.U)
    assert(!debug.WriteFullWithNarrowSize,
        "WriteNoSnpFull with non-full (64B) Size")

    /*
    * @assertion NonZeroLikelyShared
    *   The 'LikelyShared' was only allowed to be 0.
    */
    if (param.enableLikelySharedCheck) {
        debug.NonZeroLikelyShared := XZBarrier(regRXREQ.flitv, regRXREQ.flit.LikelyShared.get =/= 0.U)
        assert(!debug.NonZeroLikelyShared,
            "[optional: enableLikelySharedCheck] non-zero LikelyShared")
    } else {
        debug.NonZeroLikelyShared := false.B
    }

    /*
    * @assertion PrefetchTgtWithNonZeroAllowRetry
    *   The 'AllowRetry' of 'PrefetchTgt' was allowed to be deasserted only.
    */
    if (param.enablePrefetchTgtAllowRetryCheck) {
        debug.PrefetchTgtWithNonZeroAllowRetry := XZBarrier(regRXREQ.flitv, uDecoder.is(
                                                         PrefetchTgt
                                                     ) && regRXREQ.flit.AllowRetry.get =/= 0.U)
        assert(!debug.PrefetchTgtWithNonZeroAllowRetry,
            "[optional: enablePrefetchTgtAllowRetryCheck] PrefetchTgt with non-zero AllowRetry")
    } else {
        debug.PrefetchTgtWithNonZeroAllowRetry := false.B
    }

    /*
    * @assertion ZeroFirstAllowRetry
    *   The 'AllowRetry' of first flit must be asserted.
    *   * For NCB not supporting RetryAck, all flits received on RXREQ
    *     were expected to have 'AllowRetry = 1'.
    */
    if (param.enableFirstAllowRetryCheck) {
        debug.ZeroFirstAllowRetry := XZBarrier(regRXREQ.flitv, regRXREQ.flit.AllowRetry.get =/= 1.U)
        assert(!debug.ZeroFirstAllowRetry,
            "[optional: enableFirstAllowRetryCheck] zero first AllowRetry")
    } else {
        debug.ZeroFirstAllowRetry := false.B
    }

    /*
    * @assertion WriteWithIllegalOrder
    *   The 'Order' of write transactions is only allowed to be:
    *       - No Ordering
    */
    debug.WriteWithIllegalOrder := XZBarrier(regRXREQ.flitv, VecInit(Seq(
        WriteNoSnpFull,
        WriteNoSnpPtl
    ).map(opcode => {
        VecInit(
            (EnumCHIFieldOrder.all -= CHIFieldOrder.NoOrdering).toSeq
        .map(order => {
            val debugWireWriteWithIllegalOrder = uDecoder.is(opcode) &&
                order.U === regRXREQ.flit.Order.get

            assert(!debugWireWriteWithIllegalOrder,
                s"illegal Order of write transaction: ${opcode.name} with: ${order.displayName}")

            dontTouch(debugWireWriteWithIllegalOrder.suggestName(
                s"${opcode.name}_${order.name()}"))
        })).asUInt.orR
    })).asUInt.orR)

    /*
    * @assertion ReadWithIllegalOrder
    *   The 'Order' of read transactions is only allowed to be:
    *       - No Ordering
    *       - Request Accepted
    */
    debug.ReadWithIllegalOrder := XZBarrier(regRXREQ.flitv, VecInit(Seq(
        ReadNoSnp
    ).map(opcode => {
        VecInit(
            (EnumCHIFieldOrder.all -= CHIFieldOrder.NoOrdering
                                   -= CHIFieldOrder.RequestAccepted).toSeq
        .map(order => {
            val debugWireReadWithIllegalOrder = uDecoder.is(opcode) &&
                order.U === regRXREQ.flit.Order.get

            assert(!debugWireReadWithIllegalOrder,
                s"illegal Order of read transaction: ${opcode.name} with: ${order.displayName}")

            dontTouch(debugWireReadWithIllegalOrder.suggestName(
                s"${opcode.name}_${order.name()}"))
        })).asUInt.orR
    })).asUInt.orR)

    /*
    * @assertion DatalessWithIllegalOrder
    *   The 'Order' of dataless transactions is only allowed to be:
    *       - No Ordering
    */
    debug.DatalessWithIllegalOrder := XZBarrier(regRXREQ.flitv, VecInit(Seq(
        CleanShared,
        CleanSharedPersist,
        CleanInvalid,
        MakeInvalid,
        PrefetchTgt
    ).map(opcode => {
        VecInit(
            (EnumCHIFieldOrder.all -= CHIFieldOrder.NoOrdering).toSeq
        .map(order => {
            val debugWireDatalessWithIllegalOrder = uDecoder.is(opcode) &&
                order.U === regRXREQ.flit.Order.get

            assert(!debugWireDatalessWithIllegalOrder,
                s"illegal Order of dataless transaction: ${opcode.name} with: ${order.displayName}")

            dontTouch(debugWireDatalessWithIllegalOrder.suggestName(
                s"${opcode.name}_${order.name()}"))
        })).asUInt.orR
    })).asUInt.orR)

    /* 
    * @assertion AllowRetryWithNonZeroPCrdType
    *   The 'PCrdType' is expected to be 0 when 'AllowRetry = 1'.
    */
    if (param.enablePCrdTypeCheck) {
        debug.AllowRetryWithNonZeroPCrdType := XZBarrier(regRXREQ.flitv,
            regRXREQ.flit.AllowRetry.get === 1.U && regRXREQ.flit.PCrdType.get =/= 0.U)
        assert(!debug.AllowRetryWithNonZeroPCrdType,
            "[optional: enablePCrdTypeCheck] non-zero PCrdType on AllowRetry = 1")
    } else {
        debug.AllowRetryWithNonZeroPCrdType := false.B
    }

    /*
    * @assertion IllegalMemAttr
    *   Only the 'EWA' of 'MemAttr' is allowed to be asserted.
    *   'Device', 'Allocate' and 'Cacheable' are not legal.
    */
    debug.IllegalMemAttr := XZBarrier(regRXREQ.flitv, VecInit(Seq(
        CHIFieldMemAttr.Device,
        CHIFieldMemAttr.Allocate,
        CHIFieldMemAttr.Cacheable
    ).map(memAttr => {
        val debugWireIllegalMemAttr = memAttr.is(regRXREQ.flit.MemAttr.get)

        assert(!debugWireIllegalMemAttr,
            s"illegal MemAttr asserted: ${memAttr.displayName}")

        dontTouch(debugWireIllegalMemAttr.suggestName(
            s"${memAttr.displayName}"))
    })).asUInt.orR)

    /*
    * @assertion NonZeroSnpAttr 
    *   'SnpAttr' is expected always to be 0.
    */
    debug.NonZeroSnpAttr := XZBarrier(regRXREQ.flitv, regRXREQ.flit.SnpAttr.get =/= 0.U)
    assert(!debug.NonZeroSnpAttr,
        "non-zero SnpAttr")

    /*
    * @assertion NonZeroExcl
    *   'Excl' is expected always to be 0.
    */
    debug.NonZeroExcl := XZBarrier(regRXREQ.flitv, regRXREQ.flit.Excl.get =/= 0.U)
    assert(!debug.NonZeroExcl,
        "non-zero Excl")

    /*
    * @assertion NonZeroExpCompAck
    *   'ExpCompAck' is expected always to be 0. 
    */
    debug.NonZeroExpCompAck := XZBarrier(regRXREQ.flitv, regRXREQ.flit.ExpCompAck.get =/= 0.U)
    assert(!debug.NonZeroExpCompAck,
        "non-zero ExpCompAck")

    /*
    * @assertion IllegalSize
    *   'Size' receiving reserved value on CHI Flit error. 
    */
    debug.IllegalSize := XZBarrier(regRXREQ.flitv, !CHIFieldSize.Size1B .is(regRXREQ) &&
                                                      !CHIFieldSize.Size2B .is(regRXREQ) &&
                                                      !CHIFieldSize.Size4B .is(regRXREQ) &&
                                                      !CHIFieldSize.Size8B .is(regRXREQ) &&
                                                      !CHIFieldSize.Size16B.is(regRXREQ) &&
                                                      !CHIFieldSize.Size32B.is(regRXREQ) &&
                                                      !CHIFieldSize.Size64B.is(regRXREQ))
    assert(!debug.IllegalSize,
        "illegal Size with reserved value")
}