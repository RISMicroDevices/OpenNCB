package cn.rismd.openncb.util

import chisel3._


/*
* Valid Mux.
* 
* * The output is forced to all zero when 'valid' not asserted. 
*/
object ValidMux {

    def apply(valid: Bool, bit: Bool): Bool =
        Mux(valid, bit, dontTouch(WireInit(false.B)))

    def apply[T <: Data](valid: Bool, bits: T): T =
        Mux(valid, bits, dontTouch(WireInit(0.U.asTypeOf(bits)))) 
}


/*
* Barrier of X-state and Z-state for SystemVerilog simulation by valid signal. 
* 
* * NOTICE: Not recommended to be applied for multi-bit critical control paths
*           or wide data paths,
*           mitigation of X,Z-state were not meant to produce hardware overheads.
*/
object XZBarrier {

    def apply(valid: Bool, bit: Bool): Bool = ValidMux(valid, bit)

    @deprecated(message = "multi-bit mitigation produces hardware overheads")
    def apply[T <: Data](valid: Bool, bits: T): T = ValidMux(valid, bits)
}
