package cn.rismd.openncb

import chisel3.UInt

package object chi {

    /*
    * Convert to literal UInt instance with the width of originally specified
    * channel Opcode.
    * 
    * @see cn.rismd.openncb.chi.CHIOpcode#asUInt
    */
    implicit class fromCHIOpcodeToUInt(opcode: CHIOpcode) {
        def U: UInt = opcode.asUInt
    }
}
