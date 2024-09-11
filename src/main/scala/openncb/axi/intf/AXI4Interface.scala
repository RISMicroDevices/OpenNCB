package cc.xiangshan.openncb.axi.intf

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.Irrevocable
import cc.xiangshan.openncb.axi.bundle._
import cc.xiangshan.openncb.axi.channel._
import cc.xiangshan.openncb.axi.AXI4ParametersKey


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

        // AW channel
        aw.ready                    := rocketchip.aw.ready
        rocketchip.aw.valid         := aw.valid
        rocketchip.aw.bits          := aw.bits.asToRocketChip

        // W channel
        w.ready                     := rocketchip.w.ready
        rocketchip.w.valid          := w.valid
        rocketchip.w.bits           := w.bits.asToRocketChip

        // B channel
        rocketchip.b.ready          := b.ready
        b.valid                     := rocketchip.b.valid
        b.bits.asFromRocketChip     := rocketchip.b.bits

        // AR channel
        ar.ready                    := rocketchip.ar.ready
        rocketchip.ar.valid         := ar.valid
        rocketchip.ar.bits          := ar.bits.asToRocketChip

        // R channel
        rocketchip.r.ready          := r.ready
        r.valid                     := rocketchip.r.valid
        r.bits.asFromRocketChip     := rocketchip.r.bits

        //
        rocketchip
    }

    /* 
    * Connect this bundle to (as slave interface) rocket-chip bundle.
    * 
    * @return Connected {@code freechips.rocketchip.amba.axi4.AXI4Bundle}.
    */
    def asFromRocketChip = {
        val rocketchip  = Wire(asRocketChip)

        // AW channel
        rocketchip.aw.ready         := aw.ready
        aw.valid                    := rocketchip.aw.valid
        aw.bits.asFromRocketChip    := rocketchip.aw.bits

        // W channel
        rocketchip.w.ready          := w.ready
        w.valid                     := rocketchip.w.valid
        w.bits.asFromRocketChip     := rocketchip.w.bits

        // B channel
        b.ready                     := rocketchip.b.ready
        rocketchip.b.valid          := b.valid
        rocketchip.b.bits           := b.bits.asToRocketChip

        // AR channel
        rocketchip.ar.ready         := ar.ready
        ar.valid                    := rocketchip.ar.valid
        ar.bits.asFromRocketChip    := rocketchip.ar.bits

        // R channel
        r.ready                     := rocketchip.r.ready
        rocketchip.r.valid          := r.valid
        rocketchip.r.bits           := r.bits.asToRocketChip

        //
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

