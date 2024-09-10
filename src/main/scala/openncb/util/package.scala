package cc.xiangshan.openncb

import chisel3._
import chisel3.experimental.VecLiterals._
import chisel3.util.IrrevocableIO
import chisel3.util.Irrevocable
import chisel3.util.DecoupledIO
import chisel3.util.Decoupled


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

    implicit class irrevocableIOMapFunction[+T <: Data](gen: IrrevocableIO[T]) {

        /** Applies the supplied functor to the bits of this interface, returning a new
        * typed IrrevocableIO interface, with direciton of:
        *   B = A.mapTo(B) : 
        *      B.bits  := A.bits
        *      B.valid := A.valid
        *      A.ready := B.ready                
        * 
        * @param f The function to apply to this IrrevocableIO's 'bits' with return type B
        * @return a new IrrevocableIO of type B
        */
        def mapTo[B <: Data](f: T => B): IrrevocableIO[B] = {
            val _map_bits = f(gen.bits)
            val _map = Wire(Irrevocable(chiselTypeOf(_map_bits)))
            _map.bits   := _map_bits
            _map.valid  := gen.valid
            gen.ready   := _map.ready
             _map
        }

        /** Applies the supplied functor to the bits of this interface, returning a new
        * typed IrrevocableIO interface, with direciton of:
        *   B = A.mapFrom(B) : 
        *      A.bits  := B.bits
        *      A.valid := B.valid
        *      B.ready := A.ready                
        * 
        * @param f The function to apply to this IrrevocableIO's 'bits' with return type B
        * @return a new IrrevocableIO of type B
        */
        def mapFrom[B <: Data](f: T => B): IrrevocableIO[B] = {
            val _map_bits = f(gen.bits)
            val _map = Wire(Irrevocable(chiselTypeOf(_map_bits)))
            _map.bits   := _map_bits
            gen.valid   := _map.valid
            _map.ready  := gen.ready
             _map
        }
    }

    implicit class decoupledIOMapFunction[+T <: Data](gen: DecoupledIO[T]) {

        /** Applies the supplied functor to the bits of this interface, returning a new
        * typed DecoupledIO interface, with direciton of:
        *   A.mapTo(B) : 
        *      B.bits  := A.bits
        *      B.valid := A.valid
        *      A.ready := B.ready                
        * 
        * @param f The function to apply to this DecoupledIO's 'bits' with return type B
        * @return a new DecoupledIO of type B
        */
        def mapTo[B <: Data](f: T => B): DecoupledIO[B] = {
            val _map_bits = f(gen.bits)
            val _map = Wire(Decoupled(chiselTypeOf(_map_bits)))
            _map.bits   := _map_bits
            _map.valid  := gen.valid
            gen.ready   := _map.ready
             _map
        }

        /** Applies the supplied functor to the bits of this interface, returning a new
        * typed DecoupledIO interface, with direciton of:
        *   A.mapFrom(B) : 
        *      A.bits  := B.bits
        *      A.valid := B.valid
        *      B.ready := A.ready                
        * 
        * @param f The function to apply to this DecoupledIO's 'bits' with return type B
        * @return a new DecoupledIO of type B
        */
        def mapFrom[B <: Data](f: T => B): DecoupledIO[B] = {
            val _map_bits = f(gen.bits)
            val _map = Wire(Decoupled(chiselTypeOf(_map_bits)))
            _map.bits   := _map_bits
            gen.valid   := _map.valid
            _map.ready  := gen.ready
             _map
        }
    }
}
