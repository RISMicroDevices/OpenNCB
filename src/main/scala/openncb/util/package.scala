package cc.xiangshan.openncb

import chisel3._
import chisel3.experimental.VecLiterals._


package object util {

    implicit class fromBooleanToVecLiteral(boolean: Boolean) {

        /*
        * Boolean to Bool Vec conversion, recommended style for constants. 
        * 
        * @param n Width of Bool Vec
        */
        def BVec(n: Int): Vec[Bool] = Vec(n, boolean.B)

        /*
        * Boolean to Bool Vec Literal conversion, recommended style for constants. 
        * 
        * * Typically used for Vec.Lit
        * 
        * @param n Width of Bool Vec Literal
        */
        def BVecLit(n: Int): Vec[Bool] = 
            Vec(n, Bool()).Lit(Seq.fill(n)(boolean.B).zipWithIndex.map(l => l.swap) : _*)
    }

    implicit class sliceBitExtractionForUInt(data: UInt) {
        
        /*
        * Create a sub-slice bit extraction of the UInt by 'offset' and 'width'.
        *
        * @param offset Offset of UInt slice extraction
        * @param width  Width of UInt slice extraction
        */
        def extract(offset: Int, width: Int): UInt =
            data((offset + 1) * width - 1, offset * width)
    }
}
