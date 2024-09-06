package cc.xiangshan.openncb.axi.field

import chisel3._
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size1B => Size1B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size2B => Size2B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size4B => Size4B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size8B => Size8B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size16B => Size16B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size32B => Size32B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size64B => Size64B}
import cc.xiangshan.openncb.axi.field.AXI4FieldAxSIZE.{Size128B => Size128B}


/*
* AxSIZE field helper. 
*/
sealed class EnumAXI4FieldAxSIZE(ordinal            : Int,
                                 name               : String,
                                 val value          : Int,
                                 val displayName    : String,
                                 val sizeInBytes    : Int)
        extends Enum[EnumAXI4FieldAxSIZE](name, ordinal) {

    def sizeInBits = sizeInBytes * 8

    /* 
    * Hardware decoder.
    */
    def is(size: UInt): Bool = size === value.U(AXI4FieldAxSIZE.width.W)
}

object AXI4FieldAxSIZE {

    // AxSIZE field width
    val width               : Int       = 3

    /*
    * AxSIZE field value encodings. 
    */
    val SIZE_1B             : Int       = 0x00  // 0b000
    val SIZE_2B             : Int       = 0x01  // 0b001
    val SIZE_4B             : Int       = 0x02  // 0b010
    val SIZE_8B             : Int       = 0x03  // 0b011
    val SIZE_16B            : Int       = 0x04  // 0b100
    val SIZE_32B            : Int       = 0x05  // 0b101
    val SIZE_64B            : Int       = 0x06  // 0b110
    val SIZE_128B           : Int       = 0x07  // 0b111
    /**/

    /*
    * AxSIZE field enumerations. 
    */
    val Size1B              : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(0, "Size1B"  , SIZE_1B  , "1B"  , 1  )
    val Size2B              : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(1, "Size2B"  , SIZE_2B  , "2B"  , 2  )
    val Size4B              : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(2, "Size4B"  , SIZE_4B  , "4B"  , 4  )
    val Size8B              : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(3, "Size8B"  , SIZE_8B  , "8B"  , 8  )
    val Size16B             : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(4, "Size16B" , SIZE_16B , "16B" , 16 )
    val Size32B             : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(5, "Size32B" , SIZE_32B , "32B" , 32 )
    val Size64B             : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(6, "Size64B" , SIZE_64B , "64B" , 64 )
    val Size128B            : EnumAXI4FieldAxSIZE   = new EnumAXI4FieldAxSIZE(7, "Size128B", SIZE_128B, "128B", 128)
    /**/

    /*
    * AxSIZE field hardware comparators.
    */
    def is1B    (size: UInt): Bool  = size === SIZE_1B  .U(width.W)
    def is2B    (size: UInt): Bool  = size === SIZE_2B  .U(width.W)
    def is4B    (size: UInt): Bool  = size === SIZE_4B  .U(width.W)
    def is8B    (size: UInt): Bool  = size === SIZE_8B  .U(width.W)
    def is16B   (size: UInt): Bool  = size === SIZE_16B .U(width.W)
    def is32B   (size: UInt): Bool  = size === SIZE_32B .U(width.W)
    def is64B   (size: UInt): Bool  = size === SIZE_64B .U(width.W)
    def is128B  (size: UInt): Bool  = size === SIZE_128B.U(width.W)
    /**/
}

object EnumAXI4FieldAxSIZE {

    //
    def allElements: Seq[EnumAXI4FieldAxSIZE]
        = Seq(Size1B, Size2B, Size4B, Size8B, Size16B, Size32B, Size64B, Size128B)

    def all = allElements.toBuffer
}
