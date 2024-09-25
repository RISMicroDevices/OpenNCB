package cc.xiangshan.openncb.logical

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.EnumAXIMasterOrder
import cc.xiangshan.openncb.WithNCBParameters
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.chi.CHIConstants
import cc.xiangshan.openncb.chi.opcode.CHISNFOpcodesREQ
import cc.xiangshan.openncb.chi.channel.CHIChannelRXREQ
import cc.xiangshan.openncb.chi.field.CHIFieldRespErr
import cc.xiangshan.openncb.chi.field.CHIFieldSize
import cc.xiangshan.openncb.chi.field.CHIFieldOrder
import cc.xiangshan.openncb.chi.field.CHIFieldMemAttr
import cc.xiangshan.openncb.chi.field.EnumCHIFieldOrder
import cc.xiangshan.openncb.logical.chi.CHILinkCreditManagerRX
import cc.xiangshan.openncb.logical.chi.CHILinkActiveManagerRX
import cc.xiangshan.openncb.util.XZBarrier
import cc.xiangshan.openncb.util.ValidMux
import cc.xiangshan.openncb.debug.CompanionConnection
import cc.xiangshan.openncb.debug.DebugBundle
import cc.xiangshan.openncb.debug.DebugSignal


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
    protected val uLinkCreditProvideBuffer  = uLinkCredit.attachLinkCreditProvideBuffer()
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

    protected val logicTransactionRead  = uDecoder.is(ReadNoSnp)
    protected val logicTransactionWrite = uDecoder.is(WriteNoSnpPtl, WriteNoSnpFull)
    protected val logicLCrdReturn       = uDecoder.is(ReqLCrdReturn)


    // link credit consume on flit valid
    uLinkCredit.io.monitorCreditConsume := regRXREQ.flitv & !logicLCrdReturn

    // link credit return by ReqLCrdReturn
    uLinkCredit.io.monitorCreditReturn  := logicLCrdReturn


    // transaction allocation
    io.freeListAllocate.en := regRXREQ.flitv & !logicLCrdReturn


    // payload allocation
    require(CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH == 512,
        s"unsupported CHI packet max size ${CHIConstants.CHI_MAX_PACKET_DATA_BITS_WIDTH}" +
        " for TransactionPayload allocation logic")

    io.payloadAllocate.en       := regRXREQ.flitv & !logicLCrdReturn
    io.payloadAllocate.strb     := io.freeListAllocate.strb
    io.payloadAllocate.upload   := logicTransactionRead
    io.payloadAllocate.mask     := {
        if (paramAXI4.dataWidth == 512) {
            VecInit.fill(1)(true.B)
        } else if (paramAXI4.dataWidth == 256) {
            MuxCase(VecInit((0 until 2).map(i => regRXREQ.flit.Addr.get(5, 5) === i.U)), Seq((
                CHIFieldSize.Size64B.is(regRXREQ)   -> {
                    VecInit.fill(2)(true.B)
                }
            )))
        } else if (paramAXI4.dataWidth == 128) {
            MuxCase(VecInit((0 until 4).map(i => regRXREQ.flit.Addr.get(5, 4) === i.U)), Seq(
                CHIFieldSize.Size64B.is(regRXREQ)   -> {
                    VecInit.fill(4)(true.B)
                },
                CHIFieldSize.Size32B.is(regRXREQ)   -> {
                    VecInit((0 until 4).map(i => regRXREQ.flit.Addr.get(5, 5) === (i >> 1).U))
                }
            ))
        } else if (paramAXI4.dataWidth == 64) {
            MuxCase(VecInit((0 until 8).map(i => regRXREQ.flit.Addr.get(5, 3) === i.U)), Seq(
                CHIFieldSize.Size64B.is(regRXREQ)   -> {
                    VecInit.fill(8)(true.B)
                },
                CHIFieldSize.Size32B.is(regRXREQ)   -> {
                    VecInit((0 until 8).map(i => regRXREQ.flit.Addr.get(5, 5) === (i >> 2).U))
                },
                CHIFieldSize.Size16B.is(regRXREQ)   -> {
                    VecInit((0 until 8).map(i => regRXREQ.flit.Addr.get(5, 4) === (i >> 1).U))
                }
            ))
        } else if (paramAXI4.dataWidth == 32) {
            MuxCase(VecInit((0 until 16).map(i => regRXREQ.flit.Addr.get(5, 2) === i.U)), Seq(
                CHIFieldSize.Size64B.is(regRXREQ)   -> {
                    VecInit.fill(16)(true.B)
                },
                CHIFieldSize.Size32B.is(regRXREQ)   -> {
                    VecInit((0 until 16).map(i => regRXREQ.flit.Addr.get(5, 5) === (i >> 3).U))
                },
                CHIFieldSize.Size16B.is(regRXREQ)   -> {
                    VecInit((0 until 16).map(i => regRXREQ.flit.Addr.get(5, 4) === (i >> 2).U))
                },
                CHIFieldSize.Size8B .is(regRXREQ)   -> {
                    VecInit((0 until 16).map(i => regRXREQ.flit.Addr.get(5, 3) === (i >> 1).U))
                }
            ))
        } else {
            throw new IllegalArgumentException(s"unsupported AXI data width: ${paramAXI4.dataWidth}")
        }
    }

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
    ) && (CHIFieldOrder.RequestAccepted.is(regRXREQ)
      || (CHIFieldOrder.EndpointOrder.is(regRXREQ) && paramNCB.acceptOrderEndpoint.B))

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
    } || paramNCB.axiAWAfterFirstData.B

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
    io.queueAllocate.bits.operand.chi.Addr  := regRXREQ.flit.Addr.get

    io.queueAllocate.bits.operand.chi.WriteFull := uDecoder.is(WriteNoSnpFull)
    io.queueAllocate.bits.operand.chi.WritePtl  := uDecoder.is(WriteNoSnpPtl)

    io.queueAllocate.bits.operand.chi.ReadRespErr   := CHIFieldRespErr.OK.U
    io.queueAllocate.bits.operand.chi.WriteRespErr  := CHIFieldRespErr.OK.U

    io.queueAllocate.bits.operand.chi.Critical  := VecInit({

        if (paramUpstreamMaxBeatCount > 1)
            (0 until paramUpstreamMaxBeatCount).map(i => {
                io.queueAllocate.bits.operand.chi.Addr(5, 6 - paramUpstreamMaxBeatCountWidth) === i.U
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
    if (!paramNCB.axiBurstAlwaysIncr)
        io.queueAllocate.bits.operand.axi.Addr  := regRXREQ.flit.Addr.get
    else
    {
        // *NOTICE: Starting address of AXI INCR transfers needed to be re-aligend
        //          to the boundary of transfer size.
        //          This is because that, in CHI, the unaligned transfers were
        //          always wrapped around the address bound, but not for those
        //          INCR transfers in AXI.
        //
        // *WARNING: Unaligned CHI wrapping around transactions with Device attribute
        //           might could not be handled correctly here.
        //           With forced INCR burst, the original address information of Device
        //           transactions might be altered or dropped, leading to unexpected
        //           and undefined behaviours.
        //           Careful on implementing a special system that might rely on unaligned
        //           addresses to work.
        //           Use 'acceptMisalignedAroundDevice' to disable check.
    }

    when (CHIFieldSize.Size1B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := regRXREQ.flit.Addr.get

        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size1B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

        if (!paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        else
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        
    }.elsewhen (CHIFieldSize.Size2B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 1, 0.U(1.W))

        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size2B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

        if (!paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        else
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        
    }.elsewhen (CHIFieldSize.Size4B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 2, 0.U(2.W))

        io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
        io.queueAllocate.bits.operand.axi.Len   := 0.U

        if (!paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
        else
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        
    }.elsewhen (CHIFieldSize.Size8B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 3, 0.U(3.W))

        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
            
        } else {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        }
    }.elsewhen (CHIFieldSize.Size16B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 4, 0.U(4.W))

        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U
            
            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        }
    }.elsewhen (CHIFieldSize.Size32B.is(regRXREQ)) {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 5, 0.U(5.W))
        
        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 7.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 128) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else {
            io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size32B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        }
    }.otherwise /*(CHIFieldSize.Size64B.is(regRXREQ))*/ {
        if (paramNCB.axiBurstAlwaysIncr)
            io.queueAllocate.bits.operand.axi.Addr  := Cat(regRXREQ.flit.Addr.get >> 6, 0.U(6.W))

        if (paramAXI4.dataWidth == 32) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size4B.U
            io.queueAllocate.bits.operand.axi.Len   := 15.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 64) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size8B.U
            io.queueAllocate.bits.operand.axi.Len   := 7.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 128) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size16B.U
            io.queueAllocate.bits.operand.axi.Len   := 3.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else if (paramAXI4.dataWidth == 256) {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size32B.U
            io.queueAllocate.bits.operand.axi.Len   := 1.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.WRAP.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U

        } else {
            io.queueAllocate.bits.operand.axi.Size  := AXI4FieldAxSIZE.Size64B.U
            io.queueAllocate.bits.operand.axi.Len   := 0.U

            if (!paramNCB.axiBurstAlwaysIncr)
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.FIXED.U
            else
                io.queueAllocate.bits.operand.axi.Burst := AXI4FieldAxBURST.INCR.U
        }
    }

    io.queueAllocate.bits.operand.axi.Device    := {
        if (paramNCB.acceptMemAttrDevice)
            CHIFieldMemAttr.Device.is(regRXREQ)
        else
            DontCare
    }

    io.queueAllocate.bits.operand.axi.Critical  := VecInit({

        if (paramDownstreamMaxBeatCount > 1)
            (0 until paramDownstreamMaxBeatCount).map(i => {
                io.queueAllocate.bits.operand.axi.Addr(5, 6 - paramDownstreamMaxBeatCountWidth) === i.U
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
        val MisalignedAroundDevice              = Output(Bool())

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
            if (paramNCB.acceptOrderEndpoint && order == CHIFieldOrder.EndpointOrder) {
                false.B
            } else {

                val debugWireWriteWithIllegalOrder = uDecoder.is(opcode) &&
                    order.U === regRXREQ.flit.Order.get

                assert(!debugWireWriteWithIllegalOrder,
                    s"illegal Order of write transaction: ${opcode.name} with: ${order.displayName}")

                dontTouch(debugWireWriteWithIllegalOrder.suggestName(
                    s"${opcode.name}_${order.name()}"))
            }
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
            if (paramNCB.acceptOrderEndpoint && order == CHIFieldOrder.EndpointOrder) {
                false.B
            } else {

                val debugWireReadWithIllegalOrder = uDecoder.is(opcode) &&
                    order.U === regRXREQ.flit.Order.get

                assert(!debugWireReadWithIllegalOrder,
                    s"illegal Order of read transaction: ${opcode.name} with: ${order.displayName}")

                dontTouch(debugWireReadWithIllegalOrder.suggestName(
                    s"${opcode.name}_${order.name()}"))
            }
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
        if (paramNCB.acceptMemAttrDevice  ) None else Some(CHIFieldMemAttr.Device),
        if (paramNCB.acceptMemAttrAllocate) None else Some(CHIFieldMemAttr.Allocate),
                                                      Some(CHIFieldMemAttr.Cacheable)
    ).map(memAttr => {
        if (memAttr.isDefined) {
            
            val debugWireIllegalMemAttr = ValidMux(regRXREQ.flitv, memAttr.get.is(regRXREQ))

            assert(!debugWireIllegalMemAttr,
                s"illegal MemAttr asserted: ${memAttr.get.displayName}")

            dontTouch(debugWireIllegalMemAttr.suggestName(
                s"${memAttr.get.displayName}"))
        } else {
            false.B
        }
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

    /*
    * @assertion MisalignedAroundDevice
    *   Address misalignment around address with memory attribute 'Device'.
    */
    if (!paramNCB.axiBurstAlwaysIncr || paramNCB.acceptMisalignedAroundDevice)
        debug.MisalignedAroundDevice := false.B
    else {
        debug.MisalignedAroundDevice := XZBarrier(regRXREQ.flitv, {
            MuxCase(false.B, Seq(
                CHIFieldSize.Size1B .is(regRXREQ)   -> false.B,
                CHIFieldSize.Size2B .is(regRXREQ)   -> regRXREQ.flit.Addr.get(0, 0).orR,
                CHIFieldSize.Size4B .is(regRXREQ)   -> regRXREQ.flit.Addr.get(1, 0).orR,
                CHIFieldSize.Size8B .is(regRXREQ)   -> regRXREQ.flit.Addr.get(2, 0).orR,
                CHIFieldSize.Size16B.is(regRXREQ)   -> regRXREQ.flit.Addr.get(3, 0).orR,
                CHIFieldSize.Size32B.is(regRXREQ)   -> regRXREQ.flit.Addr.get(4, 0).orR,
                CHIFieldSize.Size64B.is(regRXREQ)   -> regRXREQ.flit.Addr.get(5, 0).orR
            ))
        })
    }
    assert(!debug.MisalignedAroundDevice,
        "misalignment around device address on INCR burst")
}