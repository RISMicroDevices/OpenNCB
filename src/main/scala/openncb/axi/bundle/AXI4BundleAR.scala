package cn.rismd.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cn.rismd.openncb.axi._


/* 
* Read address channel signals bundle.
*/
class AXI4BundleAR(implicit val p: Parameters) extends AbstractAXI4Bundle {

    // id       [idWidth - 1:0]     : 'ARID' - Read address ID.
    val id          = UInt(p(AXI4ParametersKey).idWidth.W)

    // addr     [addrWidth - 1:0]   : 'ARADDR' - Read address.
    val addr        = UInt(p(AXI4ParametersKey).addrWidth.W)

    // len      [7:0]               : 'ARLEN' - Burst length.
    val len         = UInt(8.W)

    // size     [2:0]               : 'ARSIZE' - Burst size.
    val size        = UInt(3.W)

    // burst    [1:0]               : 'ARBURST' - Burst type.
    val burst       = UInt(2.W)

    // lock     [1:0]               : 'ARLOCK' - Lock type.
    val lock        = UInt(2.W)

    // cache    [3:0]               : 'ARCACHE' - Memory type.
    val cache       = UInt(4.W)

    // prot     [2:0]               ; 'ARPROT' - Protection type.
    val prot        = UInt(3.W)

    // qos      [3:0]               ; 'ARQOS' - Quality of Service.
    val qos         = UInt(4.W)

    // region   [3:0]               ; 'ARREGION' - Region identifier.
    val region      = UInt(4.W)


    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleAR}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleAR(
        p(AXI4ParametersKey).asRocketChip)
}
