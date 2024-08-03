package cn.rismd.openncb.axi

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.Decoupled


/*
*  AXI4 Slave interface.
*/
class AXI4InterfaceSlave(implicit p: Parameters) extends AbstractAXI4Interface {

    // Write Interface
    val aw      = Flipped(Decoupled(new AXI4BundleAW))
    val w       = Flipped(Decoupled(new AXI4BundleW))
    val b       = Decoupled(new AXI4BundleB)

    // Read Interface
    val ar      = Flipped(Decoupled(new AXI4BundleAR))
    val r       = Decoupled(new AXI4BundleR)
}
