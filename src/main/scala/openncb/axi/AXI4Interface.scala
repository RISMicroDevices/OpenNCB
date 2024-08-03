package cn.rismd.openncb.axi

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.Decoupled


/*
*  AXI4 interface.
*/
class AXI4Interface(implicit p: Parameters) extends AbstractAXI4Interface {

    // Write Interface
    val aw      = Decoupled(new AXI4BundleAW)
    val w       = Decoupled(new AXI4BundleW)
    val b       = Flipped(Decoupled(new AXI4BundleB))

    // Read Interface
    val ar      = Decoupled(new AXI4BundleAR)
    val r       = Flipped(Decoupled(new AXI4BundleR))
}


// Master Interface
object AXI4InterfaceMaster {
    def apply()(implicit p: Parameters) = new AXI4Interface
}

// Slave Interface
object AXI4InterfaceSlave {
    def apply()(implicit p: Parameters) = Flipped(new AXI4Interface)
}
