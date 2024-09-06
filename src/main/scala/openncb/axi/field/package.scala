package cc.xiangshan.openncb.axi

import chisel3._

package object field {
  
    /*
    * Convert AXI Field enumerations to literal UInt instance. 
    */
    implicit class fromEnumAXI4FieldAxBURSTToUInt(field: EnumAXI4FieldAxBURST) {
        def U: UInt = field.value.U
    }

    implicit class fromEnumAXI4FieldAxSIZEToUInt(field: EnumAXI4FieldAxSIZE) {
        def U: UInt = field.value.U
    }

    implicit class fromEnumAXI4FieldRESPToUInt(field: EnumAXI4FieldRESP) {
        def U: UInt = field.value.U
    }
    /**/
}

