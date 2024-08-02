package cn.rismd.openncb.chi

import org.chipsalliance.cde.config.Parameters
import chisel3._


/*
* Issue-configurable CHI Field.
* 
* Example:
*
*   * For QoS field that exists in all supported CHI Issue:
*       {@code val qos      = CHIFieldUInt(p(CHIParametersKey).qosWidth, EnumCHIIssue.B, EnumCHIIssue.E) }
*     or
*       {@code val qos      = CHIFieldUInt(p(CHIParametersKey).qosWidth) }
* 
*   * For MPAM field that only applicable in CHI Issue E:
*       {@code val mpam     = CHIFieldUInt(p(CHIParametersKey.mpamWidth), EnumCHIIssue.E) }
* 
*   * For DoNotDataPull that only applicable in CHI Issue B:
*       {@code val donotdatapull = CHIFieldUInt(p(CHIParametersKey).donotdatapullWidth, EnumCHIIssue.B) }
*/
object CHIFieldUInt {

    def apply(width: Int, issues: EnumCHIIssue*)
             (implicit p: Parameters): Option[UInt] = {

        val paramCHI = p(CHIParametersKey)

        if (width <= 0)
            None

        if (issues.isEmpty)
            Some(UInt(width.W))

        for (issue <- issues)
            if (issue == paramCHI.issue)
                Some(UInt(width.W))

        None
    }
    
    def apply(msb: Int, lsb: Int, off: Int, field: Option[UInt], issues: EnumCHIIssue*)
             (implicit p: Parameters): Option[UInt] = {

        val paramCHI = p(CHIParametersKey)

        if (field.isEmpty)
            None

        if ((msb + off) < 0 || (lsb + off) < 0 || msb < lsb)
            None

        if (issues.isEmpty)
            Some(field.get(msb + off, lsb + off))

        for (issue <- issues)
            if (issue == paramCHI.issue)
                Some(field.get(msb + off, lsb + off))

        None
    }
}
/**/
