package cn.rismd.openncb.chi

import chisel3._


/* 
* Size field helper.
*/
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
