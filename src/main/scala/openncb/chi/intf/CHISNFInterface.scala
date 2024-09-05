package cn.rismd.openncb.chi.intf

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cn.rismd.openncb.chi.channel._
import cn.rismd.openncb.chi.WithCHIParameters


/*
* CHI SN-F node interface. 
*/
class CHISNFInterface(implicit val p: Parameters) extends Bundle with WithCHIParameters {

    // RXREQ
    val rxreq               = CHIChannelRXREQ()

    // TXRSP
    val txrsp               = CHIChannelTXRSP()

    // TXDAT
    val txdat               = CHIChannelTXDAT()

    // RXDAT
    val rxdat               = CHIChannelRXDAT()

    // RXLINKACTIVE
    val rxlinkactivereq     = Input(Bool())
    val rxlinkactiveack     = Output(Bool())

    // TXLINKACTIVE
    val txlinkactivereq     = Output(Bool())
    val txlinkactiveack     = Input(Bool())
}

object CHISNFInterface {
    def apply()(implicit p: Parameters) = new CHISNFInterface
}


// Raw interface.
class CHISNFRawInterface(implicit val p: Parameters) extends Bundle with WithCHIParameters {

    // RXREQ
    val rxreq               = CHIRawChannelRXREQ()

    // TXRSP
    val txrsp               = CHIRawChannelTXRSP()

    // TXDAT
    val txdat               = CHIRawChannelTXDAT()

    // RXDAT
    val rxdat               = CHIRawChannelRXDAT()

    // RXLINKACTIVE
    val rxlinkactivereq     = Input(Bool())
    val rxlinkactiveack     = Output(Bool())

    // TXLINKACTIVE
    val txlinkactivereq     = Output(Bool())
    val txlinkactiveack     = Input(Bool())
}

object CHISNFRawInterface {
    def apply()(implicit p: Parameters) = new CHISNFRawInterface
}