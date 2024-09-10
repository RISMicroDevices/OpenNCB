package cc.xiangshan.openncb.axi.intf

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.Irrevocable
import cc.xiangshan.openncb.axi.bundle._
import cc.xiangshan.openncb.axi.channel._
import cc.xiangshan.openncb.axi.AXI4ParametersKey
import cc.xiangshan.openncb.util._


/*
*  AXI4 interface.
*/
class AXI4Interface(implicit p: Parameters) extends AbstractAXI4Interface {

    // Write Interface
    val aw      = AXI4ChannelAW()
    val w       = AXI4ChannelW()
    val b       = Flipped(AXI4ChannelB())

    // Read Interface
    val ar      = AXI4ChannelAR()
    val r       = Flipped(AXI4ChannelR())

    //
    /*
    * Convert this bundle into rocket-chip type.
    * 
    * @return {@code freechips.rocketchip.amba.axi4.AXI4Bundle}
    */
    def asRocketChip =
        new freechips.rocketchip.amba.axi4.AXI4Bundle(p(AXI4ParametersKey).asRocketChip)

    /* 
    * Connect this bundle to (as master interface) rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4Bundle}.
    */
    def asToRocketChip = {
        val rocketchip  = Wire(asRocketChip)
        rocketchip.aw   <> aw   .mapTo  (_.asToRocketChip)
        rocketchip.w    <> w    .mapTo  (_.asToRocketChip)
        rocketchip.b    <> b    .mapFrom(_.asFromRocketChip)
        rocketchip.ar   <> ar   .mapTo  (_.asToRocketChip)
        rocketchip.r    <> r    .mapFrom(_.asFromRocketChip)
        rocketchip
    }

    /* 
    * Connect this bundle to (as slave interface) rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4Bundle}.
    */
    def asFromRocketChip = {
        val rocketchip  = Wire(asRocketChip)
        rocketchip.aw   <> aw   .mapFrom(_.asFromRocketChip)
        rocketchip.w    <> w    .mapFrom(_.asFromRocketChip)
        rocketchip.b    <> b    .mapTo  (_.asToRocketChip)
        rocketchip.ar   <> ar   .mapFrom(_.asFromRocketChip)
        rocketchip.r    <> r    .mapTo  (_.asToRocketChip)
        rocketchip
    }
}


// Master Interface
object AXI4InterfaceMaster {
    def apply()(implicit p: Parameters) = new AXI4Interface

    def asRocketChip(gen: AXI4Interface)(implicit p: Parameters) = gen.asToRocketChip
}

// Slave Interface
object AXI4InterfaceSlave {
    def apply()(implicit p: Parameters) = Flipped(new AXI4Interface)

    def asRocketChip(gen: AXI4Interface)(implicit p: Parameters) = gen.asFromRocketChip
}

