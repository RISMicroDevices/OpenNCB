package cc.xiangshan.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cc.xiangshan.openncb.axi._


/* 
* Read data channel signals bundle
*/
class AXI4BundleR(implicit p: Parameters) extends AbstractAXI4Bundle {

    // id       [idWidth - 1:0]     : 'RID' - Read ID tag.
    val id          = UInt(p(AXI4ParametersKey).idWidth.W)

    // data     [dataWidth - 1:0]   : 'RDATA' - Read data.
    val data        = UInt(p(AXI4ParametersKey).dataWidth.W)

    // resp     [1:0]               : 'RRESP' - Read response.
    val resp        = UInt(2.W)

    // last     [0:0]               : 'RLAST' - Read last.
    val last        = Bool()


    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleR}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleR(
        p(AXI4ParametersKey).asRocketChip)

    /* 
    * Connect this bundle to rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleR}.
    */
    def asToRocketChip = {
        val rocketchipR     = Wire(asRocketChip)
        rocketchipR.id      := id
        rocketchipR.data    := data
        rocketchipR.resp    := resp
    //  rocketchipR.user    := DontCare
    //  rocketchipR.echo    := DontCare
        rocketchipR.last    := last
        rocketchipR
    }

    /* 
    * Connect this bundle from rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleR}.
    */
    def asFromRocketChip = {
        val rocketchipR     = Wire(asRocketChip)
        id      := rocketchipR.id
        data    := rocketchipR.data
        resp    := rocketchipR.resp
        last    := rocketchipR.last
        rocketchipR
    }
}
