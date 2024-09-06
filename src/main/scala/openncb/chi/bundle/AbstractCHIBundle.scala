package cc.xiangshan.openncb.chi.bundle

import chisel3._
import org.chipsalliance.cde.config.Parameters
import cc.xiangshan.openncb.chi._

abstract class AbstractCHIBundle(implicit val p: Parameters) extends Bundle with WithCHIParameters {

    /*
    * @param to         Assign Target.
    * @param assigns    (width: Int, off: Int, wireOrValue: UInt).
    * @return Whether the Assign Target was available.
    */
    def CHIFieldAssign(to: Option[UInt], assigns: (Int, Int, UInt)*): Boolean = {

        if (!to.isEmpty)
        {
            val bits = VecInit(to.get.asBools)

            bits.foreach(_ := false.B)

            assigns.foreach({ case (width, off, wireOrValue) => {
                (0 until width).foreach(i => {
                    bits(i + off)   := wireOrValue(i)
                })
            }})

            to.get  := bits.asUInt

            true
        }
        else
            false
    }
}
