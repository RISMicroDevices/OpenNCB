package cc.xiangshan.openncb.logical.shared

import scala.collection.mutable.ArrayBuffer
import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Up


/*
* Rotational Priority Selector.
* 
* * Generating selection index from 'valid' vector with dynamic-linear priority
*   with bi-directional binary tree propagation.
* 
* * The index is generated with the propagation of binary tree, starts with 0.
* 
* * For examples of 14 input 'valid's:
*   ----------------------------------------------------------------
*        [ <-- 0                13 --> ]
*   valid: 0 1 1 0 1 1 0 1 0 0 0 0 0 1 
*   head :         ^                   
*                  |                   
*   selected_valid: 1
*   selected_index: 4
*   ----------------------------------------------------------------
*        [ <-- 0                13 --> ]
*   valid: 0 1 1 1 1 0 0 0 1 0 0 0 0 0 
*   head :                       ^     
*                                |     
*   selected_valid: 1
*   selected_index: 1
*   ----------------------------------------------------------------
*        [ <-- 0                                                           13 --> ]
*   valid: [ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 0 ][ 1 ][ 0 ] 
*   head :                  ^             
*                           |             
*   prior: (  2)(  1)(  0)( 13)( 12)( 11)( 10)(  9)(  8)(  7)(  6)(  5)(  4)(  3)
*   selected_valid: 1
*   selected_index: 12
*   * 'prior' stands for the current priority, higher value is higher
*   ----------------------------------------------------------------
* 
* * The 'head' points to the index with the highest priority, then the priority
*   goes down in a direction in linear. The 'index' is generated among those
*   asserted 'valid' with the current priority.
* 
* @param sourceCount Count of valid source: [sourceCount - 1:0]
*/
class RotationalPrioritySelector(val sourceCount: Int) extends Module {

    /*
    * Bundle of leaf Node module port I/O. 
    */
    protected class LeafPort(val layer: Int) extends Bundle {
        val valid       = Input(Bool())
        val index       = Input(UInt((layer + 1).W))
        val select      = Output(UInt((layer + 1).W))
    }


    /*
    * Selector Node at Upper Layer.
    * 
    * * Applies to nodes at height of 3 and higher (node height starts from 1).
    * 
    * @param layer Layer count, which is {@value heightOfNode - 2}.
    */
    protected class NodeUpperLayer(val layer: Int) extends Module {
        
        // Module I/O
        val io = IO(new Bundle {
            //
            val ll      = new LeafPort(layer)
            val lr      = new LeafPort(layer)

            //
            val up      = Flipped(new LeafPort(layer + 1))
        })

        // 
        protected val select_down   = io.up.select(layer, 0)
        protected val select_flat   = io.up.select(layer + 1)

        //
        protected val select_l_reverse  = !io.ll.valid || (io.lr.valid && (select_down > io.ll.index))
        protected val select_r_reverse  = !io.lr.valid || (io.ll.valid && (select_down > io.lr.index))

        //
        protected val selected_l_index  = Cat(1.U, io.ll.index)
        protected val selected_r_index  = Cat(0.U, io.lr.index)

        // 
        io.ll.select    := Mux( select_flat, select_down, 0.U)
        io.lr.select    := Mux(!select_flat, select_down, 0.U)

        //
        io.up.valid     := io.ll.valid | io.lr.valid
        io.up.index     := Mux(select_flat,
            Mux(select_l_reverse, selected_r_index, selected_l_index),
            Mux(select_r_reverse, selected_l_index, selected_r_index))
    }

    /*
    * Selector Node at First Layer.
    * 
    * * Applies to nodes at height of 2 (node height starts from 1).
    */
    protected class NodeFirstLayer extends Module {

        // Module I/O
        val io = IO(new Bundle {
            //
            val ll_valid    = Input(Bool())

            val lr_valid    = Input(Bool())

            //
            val up          = Flipped(new LeafPort(0))
        })

        // selection logic
        protected val select = Wire(Bool())
        select  := (io.up.select.asBool && io.ll_valid) || !io.lr_valid

        //
        io.up.valid := io.ll_valid | io.lr_valid
        io.up.index := Mux(select, 1.U, 0.U)
    }

    /* 
    * Selector Terminal.
    * 
    * * Applies to nodes at height of 1 (node height starts from 1).
    * * Unused nodes have 'valid' hard-wired to zero. Let Synthesizer do the optimization.
    */
    protected class Terminal extends Module {

        // Module I/O
        val io = IO(new Bundle {
            //
            val valid               = Input(Bool())

            //
            val upper_out_valid     = Output(Bool())
        })

        // upper output
        io.upper_out_valid  := io.valid
    }


    // local parameters
    protected def sourceIndexWidth    = log2Up(sourceCount)
    protected def sourceCountUp       = 1 << sourceIndexWidth


    // variable checks
    require(sourceCount > 1, s"sourceCount > 1: sourceCount = ${sourceCount}")


    /* 
    * Module I/O
    * 
    * @io input     in_valid    : Input 'valid' vector.
    * @io input     in_head     : Input 'head', index with the highest priority.
    * 
    * @io output    out_valid   : Selected 'valid'.
    * @io output    out_index   : Selected 'index'.
    */
    val io = IO(new Bundle {
        //
        val in_valid        = Input(Vec(sourceCount, Bool()))
        val in_head         = Input(UInt(sourceIndexWidth.W))

        //
        val out_valid       = Output(Bool())
        val out_index       = Output(UInt(sourceIndexWidth.W))
    })

    //
    protected val terminals = new Array[Terminal](sourceCountUp)

    for (i <- 0 until terminals.length)
        terminals(i) = Module(new Terminal)
    
    terminals.zipWithIndex.foreach { case (terminal, i) => {
        if (i < sourceCount)
            terminal.io.valid := io.in_valid(i)
        else
            terminal.io.valid := false.B
    }}

    //
    protected val firstLayerNodes = new Array[NodeFirstLayer](sourceCountUp >> 1)

    for (i <- 0 until firstLayerNodes.length) 
        firstLayerNodes(i) = Module(new NodeFirstLayer)
    
    firstLayerNodes.zipWithIndex.foreach { case (node, i) => {
        node.io.lr_valid := terminals((i << 1) + 0).io.upper_out_valid
        node.io.ll_valid := terminals((i << 1) + 1).io.upper_out_valid
    }}

    //
    if ((sourceCountUp >> 2) != 0) {

        val upperLayerNodes = new Array[Vector[NodeUpperLayer]](sourceIndexWidth - 1)

        // first upper layer
        upperLayerNodes(0) = Vector.tabulate(sourceCountUp >> 2) { (i) =>

            val node    = Module(new NodeUpperLayer(0))

            node.io.lr <> firstLayerNodes((i << 1) + 0).io.up
            node.io.ll <> firstLayerNodes((i << 1) + 1).io.up
            node
        }

        // upper upper layer
        if ((sourceCountUp >> 3) != 0) {

            var treeWidth: Int = sourceCountUp >> 3
            var treeLayer: Int = 1

            while (treeWidth > 0) {

                upperLayerNodes(treeLayer) = Vector.tabulate(treeWidth) { (i) =>

                    val node    = Module(new NodeUpperLayer(treeLayer))

                    node.io.lr <> upperLayerNodes(treeLayer - 1)((i << 1) + 0).io.up
                    node.io.ll <> upperLayerNodes(treeLayer - 1)((i << 1) + 1).io.up
                    node
                }

                treeWidth >>= 1
                treeLayer  += 1
            }
        }

        upperLayerNodes.last(0).io.up.select    := io.in_head

        io.out_valid    := upperLayerNodes.last(0).io.up.valid
        io.out_index    := upperLayerNodes.last(0).io.up.index
    } else {

        firstLayerNodes(0).io.up.select := io.in_head

        io.out_valid    := firstLayerNodes(0).io.up.valid
        io.out_index    := firstLayerNodes(0).io.up.index
    }
}
