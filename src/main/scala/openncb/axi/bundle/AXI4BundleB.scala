package cc.xiangshan.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cc.xiangshan.openncb.axi._


/* 
* Write response channel signals bundle
*/
class AXI4BundleB(implicit p: Parameters) extends AbstractAXI4Bundle {

    // id       [idWidth - 1:0]     : 'BID' - Response ID tag.
    val id          = UInt(p(AXI4ParametersKey).idWidth.W)

    // resp     [1:0]               : 'BRESP' - Write response.
    val resp        = UInt(2.W)

    
    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleB}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleB(
        p(AXI4ParametersKey).asRocketChip)

    /* 
    * Connect this bundle to rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleB}.
    */
    def asToRocketChip = {
        val rocketchipB     = Wire(asRocketChip)
        rocketchipB.id      := id
        rocketchipB.resp    := resp
    //  rocketchipB.user    := DontCare
    //  rocketchipB.echo    := DontCare
        rocketchipB
    }

    /* 
    * Connect this bundle from rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleB}.
    */
    def asFromRocketChip = {
        val rocketchipB     = Wire(asRocketChip)
        id      := rocketchipB.id
        resp    := rocketchipB.resp
        rocketchipB
    }
}
