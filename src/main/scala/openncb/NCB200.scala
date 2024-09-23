package cc.xiangshan.openncb

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.axi.WithAXI4Parameters
import cc.xiangshan.openncb.axi.intf.AXI4InterfaceMaster
import cc.xiangshan.openncb.chi.WithCHIParameters
import cc.xiangshan.openncb.chi.intf.CHISNFInterface
import cc.xiangshan.openncb.logical._
import cc.xiangshan.openncb.logical.chi._
import cc.xiangshan.openncb.debug.DebugSignal


/*
* NCB-200: Non-coherent CHI Bridge (CHI SN-F to AXI-4 Master)
*/
class NCB200(implicit val p: Parameters) 
        extends Module with WithAXI4Parameters
                       with WithCHIParameters
                       with WithNCBParameters
{
    val io = IO(new Bundle {
        // CHI SN-F port
        val chi                 = CHISNFInterface()

        // AXI master port
        val axi                 = AXI4InterfaceMaster()
    })


    // Module: LinkActive RX Manager
    val uLinkActiveRX   = Module(new CHILinkActiveManagerRX)

    uLinkActiveRX.io.goRunReady     := true.B
    uLinkActiveRX.io.goStopReady    := true.B

    uLinkActiveRX.io.linkactiveReq  := io.chi.rxlinkactivereq
    io.chi.rxlinkactiveack          := uLinkActiveRX.io.linkactiveAck

    // Module: LinkActive TX Manager
    val uLinkActiveTX   = Module(new CHILinkActiveManagerTX)

    uLinkActiveTX.io.goActivateReady    := true.B
    uLinkActiveTX.io.goDeactivateReady  := false.B

    io.chi.txlinkactivereq          := uLinkActiveTX.io.linkactiveReq
    uLinkActiveTX.io.linkactiveAck  := io.chi.txlinkactiveack

    // Module: Address Order Maintainence CAM
    val uOrderAddressCAM        = Module(new NCBOrderAddressCAM)

    // Module: Request Order Maintainence CAM
    val uOrderRequestCAM        = Module(new NCBOrderRequestCAM)

    // Module: Transaction Free List
    val uTransactionFreeList    = Module(new NCBTransactionFreeList)

    // Module: Transaction Age Matrix
    val uTransactionAgeMatrix   = Module(new NCBTransactionAgeMatrix)

    // Module: Transaction Queue
    val uTransactionQueue       = Module(new NCBTransactionQueue)

    // Module: Transaction Data Payload
    val uTransactionPayload     = Module(new NCBTransactionPayload)

    // TXSACTIVE
    io.chi.txsactive    := !uTransactionFreeList.io.empty

    /*
    * Upstream CHI Channel Subordinates
    */
    val uRXREQ  = NCBUpstreamRXREQ(uLinkActiveRX,
                                   uTransactionFreeList,
                                   uTransactionAgeMatrix,
                                   uOrderAddressCAM,
                                   uOrderRequestCAM,
                                   uTransactionQueue,
                                   uTransactionPayload)

    val uRXDAT  = NCBUpstreamRXDAT(uLinkActiveRX,
                                   uTransactionQueue,
                                   uTransactionPayload)

    val uTXRSP  = NCBUpstreamTXRSP(uLinkActiveTX,
                                   uTransactionAgeMatrix,
                                   uTransactionQueue)

    val uTXDAT  = NCBUpstreamTXDAT(uLinkActiveTX,
                                   uTransactionAgeMatrix,
                                   uTransactionQueue,
                                   uTransactionPayload)

    io.chi.rxreq <> uRXREQ.io.rxreq
    io.chi.rxdat <> uRXDAT.io.rxdat
    io.chi.txrsp <> uTXRSP.io.txrsp
    io.chi.txdat <> uTXDAT.io.txdat
    /**/

    /*
    * Downstream AXI Channel Subordinates 
    */
    val uAW     = NCBDownstreamAW(uTransactionAgeMatrix,
                                  uTransactionQueue)
        
    val uW      = NCBDownstreamW(uTransactionQueue,
                                 uTransactionPayload,
                                 uAW)
    
    val uB      = NCBDownstreamB(uTransactionQueue,
                                 uW)

    val uAR     = NCBDownstreamAR(uTransactionAgeMatrix,
                                  uTransactionQueue)

    val uR      = NCBDownstreamR(uTransactionQueue,
                                 uTransactionPayload,
                                 uAR)
    
    io.axi.aw <> uAW.io.aw
    io.axi.w  <> uW .io.w
    io.axi.b  <> uB .io.b
    io.axi.ar <> uAR.io.ar
    io.axi.r  <> uR .io.r
    /**/


    // debug signals
    /*
    * Port I/O: Debug
    * 
    * @io output    valid   : Debug Valid, asserted on any submodule debug signal asserted.
    * @io output    reason  : Debug Valid Reason.
    */
    @DebugSignal
    val debug   = IO(new Bundle {
        val valid   = Output(Bool())
        val reason  = Output(new Bundle {
            val orderAddressCAM         = chiselTypeOf(uOrderAddressCAM.debug)
            val orderRequestCAM         = chiselTypeOf(uOrderRequestCAM.debug)
            val transactionFreeList     = chiselTypeOf(uTransactionFreeList.debug)
            val transactionQueue        = chiselTypeOf(uTransactionQueue.debug)
            val transactionPayload      = chiselTypeOf(uTransactionPayload.debug)
            val chiRXREQ                = chiselTypeOf(uRXREQ.debug)
            val chiRXDAT                = chiselTypeOf(uRXDAT.debug)
            val chiTXRSP                = chiselTypeOf(uTXRSP.debug)
            val chiTXDAT                = chiselTypeOf(uTXDAT.debug)
            val axiB                    = chiselTypeOf(uB.debug)
            val axiR                    = chiselTypeOf(uR.debug)
        })
    })

    dontTouch(debug)

    debug.valid := debug.reason.asUInt.orR

    debug.reason.orderAddressCAM        := uOrderAddressCAM.debug
    debug.reason.orderRequestCAM        := uOrderRequestCAM.debug
    debug.reason.transactionFreeList    := uTransactionFreeList.debug
    debug.reason.transactionQueue       := uTransactionQueue.debug
    debug.reason.transactionPayload     := uTransactionPayload.debug
    debug.reason.chiRXREQ               := uRXREQ.debug
    debug.reason.chiRXDAT               := uRXDAT.debug
    debug.reason.chiTXRSP               := uTXRSP.debug
    debug.reason.chiTXDAT               := uTXDAT.debug
    debug.reason.axiB                   := uB.debug
    debug.reason.axiR                   := uR.debug
}