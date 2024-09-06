package cc.xiangshan.openncb.util

import chisel3._


/*
* Standardized Addressable Read Write Port (1 Write, 1 Read)
*/
class AddressableReadWritePort(val addressWidth: Int, val dataWidth: Int) extends Bundle {

    // Write Port
    val w   = new AddressableWritePort(addressWidth, dataWidth)

    // Read Port
    val r   = new AddressableReadPort(addressWidth, dataWidth)
}
