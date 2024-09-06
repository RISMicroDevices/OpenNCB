package cc.xiangshan.openncb.axi.field

import chisel3._
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP.{OKAY => OKAY}
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP.{EXOKAY => EXOKAY}
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP.{SLVERR => SLVERR}
import cc.xiangshan.openncb.axi.field.AXI4FieldRESP.{DECERR => DECERR}


/*
* xRESP field helper. 
*/
sealed class EnumAXI4FieldRESP(ordinal              : Int,
                               name                 : String,
                               val value            : Int,
                               val displayName      : String)
        extends Enum[EnumAXI4FieldRESP](name, ordinal) {

    /*
    * Hardware decoder.
    */
    def is(size: UInt): Bool = size === value.U(AXI4FieldRESP.width.W)
}

object AXI4FieldRESP {

    // xRESP field width
    val width               : Int       = 2

    /*
    * xBURST field value encodings.
    */
    val RESP_OKAY           : Int       = 0x00 // 0b00
    val RESP_EXOKAY         : Int       = 0x01 // 0b01
    val RESP_SLVERR         : Int       = 0x02 // 0b10
    val RESP_DECERR         : Int       = 0x03 // 0b11
    /**/

    /*
    * xBURST field enumerations.
    */
    val OKAY                : EnumAXI4FieldRESP     = new EnumAXI4FieldRESP(0, "OKAY"  , RESP_OKAY  , "OKAY"  )
    val EXOKAY              : EnumAXI4FieldRESP     = new EnumAXI4FieldRESP(1, "EXOKAY", RESP_EXOKAY, "EXOKAY")
    val SLVERR              : EnumAXI4FieldRESP     = new EnumAXI4FieldRESP(2, "SLVERR", RESP_SLVERR, "SLVERR")
    val DECERR              : EnumAXI4FieldRESP     = new EnumAXI4FieldRESP(3, "DECERR", RESP_DECERR, "DECERR")
    /**/

    /*
    * xBURST field hardware comparators. 
    */
    def isOKAY  (size: UInt): Bool  = size === RESP_OKAY  .U(width.W)
    def isEXOKAY(size: UInt): Bool  = size === RESP_EXOKAY.U(width.W)
    def isSLVERR(size: UInt): Bool  = size === RESP_SLVERR.U(width.W)
    def isDECERR(size: UInt): Bool  = size === RESP_DECERR.U(width.W)
    /**/
}

object EnumAXI4FieldRESP {

    //
    def allElements: Seq[EnumAXI4FieldRESP]
        = Seq(OKAY, EXOKAY, SLVERR, DECERR)

    def all = allElements.toBuffer
}
