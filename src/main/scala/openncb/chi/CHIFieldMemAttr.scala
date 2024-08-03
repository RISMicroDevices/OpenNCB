package cn.rismd.openncb.chi

import chisel3._


/* 
* MemAttr field helper. 
*/
object CHIFieldMemAttr {

    // Allocate hint bit offset.
    val BIT_ALLOCATE        : Int   = 3

    // Cacheable bit offset.
    val BIT_CACHEABLE       : Int   = 2

    // Device bit offset.
    val BIT_DEVICE          : Int   = 1

    // Early Write Acknowledge (EWA) bit offset
    val BIT_EWA             : Int   = 0

    /*
    * Check if this MemAttr field bits had Allocate Hint bit set.
    * 
    * @param memAttr Hardware UInt instance of MemAttr field bits 
    * @return Hardware Bool instance of Allocate Hint bit
    */
    def isAllocateHint(memAttr: UInt): Bool = memAttr(BIT_ALLOCATE)

    /*
    * Check if this MemAttr field bits had Cacheable bit set. 
    * 
    * @param memAttr Hardware UInt instance of MemAttr field bits 
    * @return Hardware Bool instance of Cacheable bit
    */
    def isCacheable(memAttr: UInt): Bool = memAttr(BIT_CACHEABLE)

    /*
    * Check if this MemAttr field bits had Device bit set. 
    * 
    * @param memAttr Hardware UInt instance of MemAttr field bits 
    * @return Hardware Bool instance of Device bit
    */
    def isDevice(memAttr: UInt): Bool = memAttr(BIT_DEVICE)

    /*
    * Check if this MemAttr field bits had EWA bit set. 
    * 
    * @param memAttr Hardware UInt instance of MemAttr field bits 
    * @return Hardware Bool instance of EWA bit
    */
    def isEWA(memAttr: UInt): Bool = memAttr(BIT_EWA)
}
