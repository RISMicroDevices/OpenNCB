package cc.xiangshan.openncb.util

import chisel3._


/* 
* Hardware parallel reduction.
*/
object ParallelReduction {

    /*
    * @param x      Reduction subject.
    * @param func   Reduction function.
    */
    def apply[T <: Data](x: Seq[T], func: (T, T) => T): T = {

        require(x.nonEmpty)

        x match {

            case Seq(a)     => a
            case Seq(a, b)  => func(a, b)
            case _          => apply(Seq(
                    apply(x.take(x.size / 2), func),
                    apply(x.drop(x.size / 2), func)), 
                func)
        }
    }
}


/*
* Hardware parallel OR reduction.
*/
object ParallelOR {

    /* 
    * @param x      Reduction subject.
    */
    def apply[T <: Data](x: Seq[T]): T =
        ParallelReduction(x, (a: T, b: T) => (a.asUInt | b.asUInt).asTypeOf(x.head))
}


/* 
* Hardware parallel One-hot MUX.
*/
object ParallelMux {

    /*
    * @param in     Sequence of (Selection Enable, Data). 
    */
    def apply[T <: Data](in: Seq[(Bool, T)]): T =
        ParallelOR(in.map({ case (en, x) => ValidMux(en, x) }))
}
