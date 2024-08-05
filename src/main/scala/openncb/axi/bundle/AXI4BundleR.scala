package cn.rismd.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cn.rismd.openncb.axi._


/* 
* Read data channel signals bundle
*/
class AXI4BundleR(implicit p: Parameters) extends AbstractAXI4Bundle {

    // id       [idWidth - 1:0]     : 'RID' - Read ID tag.
    val id          = UInt(p(AXI4ParametersReadChannelKey).idWidth.W)

    // data     [dataWidth - 1:0]   : 'RDATA' - Read data.
    val data        = UInt(p(AXI4ParametersReadChannelKey).dataWidth.W)

    // resp     [1:0]               : 'RRESP' - Read response.
    val resp        = UInt(2.W)

    // last     [0:0]               : 'RLAST' - Read last.
    val last        = Bool


    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleR}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleR(
        p(AXI4ParametersReadChannelKey).asRocketChip)
}
