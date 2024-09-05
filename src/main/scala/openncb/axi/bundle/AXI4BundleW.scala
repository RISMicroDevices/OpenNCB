package cn.rismd.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cn.rismd.openncb.axi._


/* 
* Write data channel signals bundle
*/
class AXI4BundleW(implicit p: Parameters) extends AbstractAXI4Bundle {

    // data     [dataWidth - 1:0]   : 'WDATA' - Write data.
    val data        = UInt(p(AXI4ParametersKey).dataWidth.W)

    // strb     [strbWidth - 1:0]   : 'WSTRB' - Write strobes.
    val strb        = UInt(p(AXI4ParametersKey).strbWidth.W)

    // last     [0:0]               : 'WLAST' - Write last.
    val last        = Bool()


    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleW}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleW(
        p(AXI4ParametersKey).asRocketChip)
}
