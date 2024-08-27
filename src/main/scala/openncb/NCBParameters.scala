package cn.rismd.openncb

import org.chipsalliance.cde.config.Field
import chisel3.util.log2Up


case class NCBParameters (

    // NCB Parameters
    /* 
    * outstandingDepth: Configure the outstanding depth of CHI transactions of NCB.
    * 
    * * By default, {@code outstandingDepth} is set to the maximum value, 
    *   which is {@value 15}.
    * 
    * * The legal value for {@code outstandingDepth} was 1 to 15, determined by the
    *   AMBA CHI specification. The outstanding status are controlled by CHI Link Credit.
    */
    outstandingDepth        : Int           = 15,



    // TODO



    // AXI side - AW channel
    /* 
    * axiConstantAWID: Configure whether the AXI4 write channel IDs
    *                  were set to a constant value.
    *
    * * By default, {@code axiConstantAWID} is set to {@value false}.
    * 
    * * When {@code axiConstantAWID} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWID' was always tied to {@code axiConstantAWIDValue}.
    *   Otherwise, the 'AWID' was tied to the NCB-Transaction-ID (inner-NCB) of the on-going
    *   transaction, whose maximum value is decided by the configured outstanding count of
    *   CHI transactions.
    */
    axiConstantAWID         : Boolean       = false,

    /*
    * axiConstantAWIDValue: Specify the fixed value of AXI write channel ID
    *                       when {@code axiConstantAWID} set to {@value true}.
    * *  When {@code axiConstantAWID} is set to {@value true}, on the AXI4 write channel
    *    side, the output value of 'AWID' is always tied to {@code axiConstantAWIDValue}.
    */
    axiConstantAWIDValue    : Int           = 0,

    /*
    * axiConstantAWQoS: Configure whether the AXI4 write channel QoS
    *                   was set to a constant value.
    * 
    * * By default, {@code axiConstantAWQoS} is set to {@value false}.
    * 
    * * When {@code axiConstantAWQoS} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWQOS' was always tied to {@code axiConstantAWQoSValue}.
    *   Otherwise, the 'AWQOS' came from CHI side.
    */
    axiConstantAWQoS        : Boolean       = false,

    /*
    * axiConstantAWQoSValue: Specify the fixed value of AXI write channel QoS
    *                        when {@code axiConstantAWQoS} set to {@value true}.
    * 
    * * When {@code axiConstantAWQoS} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWQOS' was always tied to {@code axiConstantAWQoSValue}.
    */
    axiConstantAWQoSValue   : Int           = 0,

    /*
    * axiAWBufferable: Configure whether the AXI write channel Cache Attribute
    *                  was set with Bufferable attribute.
    * 
    * * When {@code axiAWBufferable} was set to {@value true}, on the AXI4 write channel
    *   side, the output value of 'AWCACHE' was always tied to {@value 0b0011}, indicating
    *   Normal Non-cacheable Bufferable Memory.
    *   Otherwise, the 'AWCACHE' was tied to {@value 0b0010}, indicating
    *   Normal Non-cacheable Non-bufferable Memory.
    */
    axiAWBufferable         : Boolean       = false
) {

    require(outstandingDepth >= 1 && outstandingDepth <= 15, 
        s"The legal value for 'outstandingDepth' was 1 to 15, by the AMBA CHI specification: " +
        s"outstandingDepth = ${outstandingDepth}")

    //
    def outstandingIndexWidth: Int  = log2Up(outstandingDepth)
}


case object NCBParametersKey extends Field[NCBParameters]
