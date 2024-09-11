package cc.xiangshan.openncb.logical

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.cde.config.Field
import cc.xiangshan.openncb.WithNCBParameters


/* 
* NCB Transaction Age Matrix
*/
object NCBTransactionAgeMatrix {

    case class Parameters (
    )

    case object ParametersKey extends Field[Parameters]
}

class NCBTransactionAgeMatrix(implicit val p: Parameters)
        extends Module with WithNCBParameters {
    
    // public parameters
    val param   = p.lift(NCBTransactionAgeMatrix.ParametersKey)
        .getOrElse(() => new NCBTransactionAgeMatrix.Parameters)

    // local parameters


    /*
    * Port I/O: Matrix Update
    * 
    * @io input     en      : Update Enable.
    * @io input     strb    : Update Strobe.
    */
    class UpdatePort extends Bundle {
        val en          = Input(Bool())
        val strb        = Input(Vec(paramNCB.outstandingDepth, Bool()))
    }

    /*
    * Port I/O: Selection
    * 
    * @io input     in      : Input valid for selection.
    * @io output    out     : Output valid after selection.
    */
    class SelectPort extends Bundle {
        val in          = Input (Vec(paramNCB.outstandingDepth, Bool()))
        val out         = Output(Vec(paramNCB.outstandingDepth, Bool()))
    }


    /*
    * Module I/O 
    */
    val io = IO(new Bundle {
        // update port
        val update      = new UpdatePort

        // selection port for TXRSP
        val selectTXRSP = new SelectPort

        // selection port for TXDAT
        val selectTXDAT = new SelectPort

        // selection port for AW
        val selectAW    = new SelectPort

        // selection port for AR
        val selectAR    = new SelectPort
    })


    // Age Matrix Registers
    val regAge          = Seq.fill(paramNCB.outstandingDepth)(                      // row
                          Seq.fill(paramNCB.outstandingDepth)(RegInit(false.B)))    // column
    
    // use upper matrix only to reduce mirroring registers
    def scalaGetAge(row: Int, col: Int): Bool = {

        if (row < col)
            regAge(row)(col)
        else if (row == col)
            true.B
        else
            ~regAge(col)(row)
    }

    // age matrix update
    regAge.zipWithIndex.foreach({ case (m, i) => {
        m.zipWithIndex.foreach({ case(n, j) => {

            if (i < j) {

                when (io.update.en & io.update.strb(j)) {
                    // update column to 1
                    n   := true.B
                }.elsewhen (io.update.en & io.update.strb(i)) {
                    // update row to 0
                    n   := false.B
                }
            }
        } })
    } })


    // selection logic
    Seq(
        io.selectTXRSP,
        io.selectTXDAT,
        io.selectAW,
        io.selectAR
    ).foreach (select => {
        select.out := VecInit((0 until paramNCB.outstandingDepth).map(i => {
            (VecInit((0 until paramNCB.outstandingDepth)
                .map(j => scalaGetAge(i, j))).asUInt | ~select.in.asUInt).andR & select.in(i)
        }))
    })
}