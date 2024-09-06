package cc.xiangshan.openncb.util

import chisel3._


/*
* Standardized Addressable Write Port
*/
class AddressableReadPort(val addressWidth: Int, val dataWidth: Int) extends Bundle {

    // Write Port
    val en      = Input(Bool())
    val addr    = Input(UInt(addressWidth.W))
    val data    = Output(UInt(dataWidth.W))
}

