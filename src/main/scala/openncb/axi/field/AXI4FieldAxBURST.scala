package cc.xiangshan.openncb.axi.field

import chisel3._
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST.{Fixed => Fixed}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST.{Incr => Incr}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxBURST.{Wrap => Wrap}


/*
* AxBURST field helper. 
*/
sealed class EnumAXI4FieldAxBURST(ordinal               : Int,
                                  name                  : String,
                                  val value             : Int,
                                  val displayName       : String)
        extends Enum[EnumAXI4FieldAxBURST](name, ordinal) {

    /*
    * Hardware decoder. 
    */
    def is(burst: UInt): Bool = burst === value.U(AXI4FieldAxBURST.width.W)
}

object AXI4FieldAxBURST {

    // AxBURST field width
    val width           : Int       = 2

    /*
    * AxBURST field value encodings. 
    */
    val FIXED           : Int       = 0x00
    val INCR            : Int       = 0x01
    val WRAP            : Int       = 0x03
    /**/

    /*
    * AxBURST field enumerations. 
    */
    val Fixed           : EnumAXI4FieldAxBURST  = new EnumAXI4FieldAxBURST(0, "Fixed", FIXED, "FIXED")
    val Incr            : EnumAXI4FieldAxBURST  = new EnumAXI4FieldAxBURST(1, "Incr" , INCR , "INCR" )
    val Wrap            : EnumAXI4FieldAxBURST  = new EnumAXI4FieldAxBURST(2, "Wrap" , WRAP , "WRAP" )
    /**/

    /* 
    * AxBURST field hardware comparators.
    */
    def isFixed     (burst: UInt): Bool = burst === FIXED.U(width.W)
    def isIncr      (burst: UInt): Bool = burst === INCR .U(width.W)
    def isWrap      (burst: UInt): Bool = burst === WRAP .U(width.W)
    /**/
}

object EnumAXI4FieldAxBURST {

    //
    def allElements: Seq[EnumAXI4FieldAxBURST]
        = Seq(Fixed, Incr, Wrap)

    def all = allElements.toBuffer
}
