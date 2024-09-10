package cc.xiangshan.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cc.xiangshan.openncb.axi._


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

    /* 
    * Connect this bundle to rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleR}.
    */
    def asToRocketChip = {
        val rocketchipW     = Wire(asRocketChip)
        rocketchipW.data    := data
        rocketchipW.strb    := strb
        rocketchipW.last    := last
    //  rocketchipW.user    := DontCare
        rocketchipW
    }

    /* 
    * Connect this bundle from rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleR}.
    */
    def asFromRocketChip = {
        val rocketchipW     = Wire(asRocketChip)
        data    := rocketchipW.data
        strb    := rocketchipW.strb
        last    := rocketchipW.last
        rocketchipW
    }
}
