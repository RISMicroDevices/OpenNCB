package cn.rismd.openncb.chi.field

import chisel3._


/* 
* Size field helper.
*/
sealed class EnumCHIFieldSize(ordinal               : Int,
                              name                  : String,
                              val value             : Int,
                              val displayName       : String)
        extends Enum[EnumCHIFieldSize](name, ordinal)

object CHIFieldSize {

    /*
    * Size field value encodings.
    */
    val SIZE_1B     : Int       = 0x00  // 0b000
    val SIZE_2B     : Int       = 0x01  // 0b001
    val SIZE_4B     : Int       = 0x02  // 0b010
    val SIZE_8B     : Int       = 0x03  // 0b011
    val SIZE_16B    : Int       = 0x04  // 0b100
    val SIZE_32B    : Int       = 0x05  // 0b101
    val SIZE_64B    : Int       = 0x06  // 0b110

    val Size1B      : EnumCHIFieldSize  = new EnumCHIFieldSize(0, "Size1B" , SIZE_1B , "1B" )
    val Size2B      : EnumCHIFieldSize  = new EnumCHIFieldSize(1, "Size2B" , SIZE_2B , "2B" )
    val Size4B      : EnumCHIFieldSize  = new EnumCHIFieldSize(2, "Size4B" , SIZE_4B , "4B" )
    val Size8B      : EnumCHIFieldSize  = new EnumCHIFieldSize(3, "Size8B" , SIZE_8B , "8B" )
    val Size16B     : EnumCHIFieldSize  = new EnumCHIFieldSize(4, "Size16B", SIZE_16B, "16B")
    val Size32B     : EnumCHIFieldSize  = new EnumCHIFieldSize(5, "Size32B", SIZE_32B, "32B")
    val Size64B     : EnumCHIFieldSize  = new EnumCHIFieldSize(6, "Size64B", SIZE_64B, "64B")
    /**/

    /* 
    * Size field hardware comparators. 
    */
    def is1B    (size: UInt): Bool  = size === SIZE_1B.U
    def is2B    (size: UInt): Bool  = size === SIZE_2B.U
    def is4B    (size: UInt): Bool  = size === SIZE_4B.U
    def is8B    (size: UInt): Bool  = size === SIZE_8B.U
    def is16B   (size: UInt): Bool  = size === SIZE_16B.U
    def is32B   (size: UInt): Bool  = size === SIZE_32B.U
    def is64B   (size: UInt): Bool  = size === SIZE_64B.U
    /**/
}
