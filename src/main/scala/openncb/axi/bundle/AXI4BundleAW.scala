package cc.xiangshan.openncb.axi.bundle

import org.chipsalliance.cde.config.Parameters
import chisel3._
import cc.xiangshan.openncb.axi._


/*
* Write address channel signals bundle.
*/
class AXI4BundleAW(implicit p: Parameters) extends AbstractAXI4Bundle {

    // id       [idWidth - 1:0]     : 'AWID' - Write address ID.
    val id          = UInt(p(AXI4ParametersKey).idWidth.W)

    // addr     [addrWidth - 1:0]   : 'AWADDR' - Write address.
    val addr        = UInt(p(AXI4ParametersKey).addrWidth.W)

    // len      [7:0]               : 'AWLEN' - Burst length.
    val len         = UInt(8.W)

    // size     [2:0]               : 'AWSIZE' - Burst size.
    val size        = UInt(3.W)

    // burst    [1:0]               : 'AWBURST' - Burst type.
    val burst       = UInt(2.W)

    // lock     [1:0]               : 'AWLOCK' - Lock type.
    val lock        = UInt(2.W)

    // cache    [3:0]               : 'AWCACHE' - Memory type.
    val cache       = UInt(4.W)

    // prot     [2:0]               : 'AWPROT' - Protection type.
    val prot        = UInt(3.W)

    // qos      [3:0]               : 'AWQOS' - Quality of Service.
    val qos         = UInt(4.W)

    // region   [3:0]               : 'AWREGION' - Region identifier.
    val region      = UInt(4.W)


    /*
    * Convert this bundle into rocket-chip bundle type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4BundleAW}
    */
    def asRocketChip = new freechips.rocketchip.amba.axi4.AXI4BundleAW(
        p(AXI4ParametersKey).asRocketChip)

    /* 
    * Connect this bundle to rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleAW}.
    */
    def asToRocketChip = {
        val rocketchipAW    = Wire(asRocketChip)
        rocketchipAW.id     := id
        rocketchipAW.addr   := addr
        rocketchipAW.len    := len
        rocketchipAW.size   := size
        rocketchipAW.burst  := burst
        rocketchipAW.lock   := lock
        rocketchipAW.cache  := cache
        rocketchipAW.prot   := prot
        rocketchipAW.qos    := qos
    //  rocketchipAW.user   := DontCare
    //  rocketchipAW.echo   := DontCare
        rocketchipAW
    }

    /* 
    * Connect this bundle from rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4BundleAW}.
    */
    def asFromRocketChip = {
        val rocketchipAW    = Wire(asRocketChip)
        id      := rocketchipAW.id
        addr    := rocketchipAW.addr
        len     := rocketchipAW.len
        size    := rocketchipAW.size
        burst   := rocketchipAW.burst
        lock    := rocketchipAW.lock
        cache   := rocketchipAW.cache
        prot    := rocketchipAW.prot
        qos     := rocketchipAW.qos
    //  region  := DontCare
        rocketchipAW
    }
}
