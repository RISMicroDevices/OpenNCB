package cn.rismd.openncb.axi

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.Decoupled


/*
*  AXI4 Master interface.
*/
class AXI4InterfaceMaster(implicit p: Parameters) extends AbstractAXI4Interface {

    // Write Interface
    val aw      = Decoupled(new AXI4BundleAW)
    val w       = Decoupled(new AXI4BundleW)
    val b       = Decoupled(new AXI4BundleB)

    // Read Interface
    val ar      = Decoupled(new AXI4BundleAR)
    val r       = Decoupled(new AXI4BundleR)
}

