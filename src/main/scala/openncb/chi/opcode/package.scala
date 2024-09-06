package cc.xiangshan.openncb.chi

import chisel3.UInt

package object opcode {

    /*
    * Convert to literal UInt instance with the width of originally specified
    * channel Opcode.
    * 
    * @see cc.xiangshan.openncb.chi.CHIOpcode#asUInt
    */
    implicit class fromCHIOpcodeToUInt(opcode: CHIOpcode) {
        def U: UInt = opcode.asUInt
    }
}
