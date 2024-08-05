package cn.rismd.openncb.logical.shared

import scala.collection.mutable.ArrayBuffer
import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Up


class RotationalPrioritySelector(val sourceCount: Int) extends Module {

    // Selector Node at Upper Layer
    protected class NodeUpperLayer(val layer: Int, val index: Int) extends Module {
        
        // Module I/O
        val io = IO(new Bundle {
            //
            val lower_l_in_valid    = Input(Bool())
            val lower_l_in_index    = Input(UInt((layer + 1).W))
            val lower_l_out_select  = Output(UInt((layer + 1).W))

            val lower_r_in_valid    = Input(Bool())
            val lower_r_in_index    = Input(UInt((layer + 1).W))
            val lower_r_out_select  = Output(UInt((layer + 1).W))

            //
            val upper_in_select     = Input(UInt((layer + 2).W))
            val upper_out_valid     = Output(Bool())
            val upper_out_index     = Output(UInt((layer + 2).W))
        })

        // 
        protected val select_down   = io.upper_in_select(layer, 0)
        protected val select_flat   = io.upper_in_select(layer + 1)

        //
        protected val select_l_reverse  = !io.lower_l_in_valid || (io.lower_r_in_valid && (select_down > io.lower_l_in_index))
        protected val select_r_reverse  = !io.lower_r_in_valid || (io.lower_l_in_valid && (select_down > io.lower_r_in_index))

        //
        protected val selected_l_index  = Cat(1.U, io.lower_l_in_index)
        protected val selected_r_index  = Cat(0.U, io.lower_r_in_index)

        // 
        io.lower_l_out_select   := Mux( select_flat, select_down, 0.U)
        io.lower_r_out_select   := Mux(!select_flat, select_down, 0.U)

        //
        io.upper_out_valid  := io.lower_l_in_valid | io.lower_r_in_valid
        io.upper_out_index  := Mux(select_flat,
            Mux(select_l_reverse, selected_r_index, selected_l_index),
            Mux(select_r_reverse, selected_l_index, selected_r_index))
    }

    // Selector Node at First Layer
    protected class NodeFirstLayer extends Module {

        // Module I/O
        val io = IO(new Bundle {
            //
            val lower_l_in_valid    = Input(Bool())

            val lower_r_in_valid    = Input(Bool())

            //
            val upper_in_select     = Input(Bool())
            val upper_out_valid     = Output(Bool())
            val upper_out_index     = Output(UInt(1.W))
        })

        // selection logic
        protected val select = Wire(Bool())
        select := io.upper_in_select && io.lower_l_in_valid

        //
        io.upper_out_valid  := io.lower_l_in_valid | io.lower_r_in_valid
        io.upper_out_index  := Mux(select, 1.U, 0.U)
    }

    // Selector Terminal
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


    // Module I/O
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
        node.io.lower_r_in_valid := terminals((i << 1) + 0).io.upper_out_valid
        node.io.lower_l_in_valid := terminals((i << 1) + 1).io.upper_out_valid
    }}

    //
    if ((sourceCountUp >> 2) != 0) {

        val upperLayerNodes = new Array[Array[NodeUpperLayer]](sourceIndexWidth - 1)

        // first upper layer
        upperLayerNodes(0) = new Array[NodeUpperLayer](sourceCountUp >> 2)

        for (i <- 0 until upperLayerNodes(0).length)
            upperLayerNodes(0)(i) = Module(new NodeUpperLayer(0, i))
        
        upperLayerNodes(0).zipWithIndex.foreach { case (node, i) => {

            firstLayerNodes((i << 1) + 0).io.upper_in_select    := node.io.lower_r_out_select
            firstLayerNodes((i << 1) + 1).io.upper_in_select    := node.io.lower_l_out_select

            node.io.lower_r_in_index    := firstLayerNodes((i << 1) + 0).io.upper_out_index
            node.io.lower_l_in_index    := firstLayerNodes((i << 1) + 1).io.upper_out_index

            node.io.lower_r_in_valid    := firstLayerNodes((i << 1) + 0).io.upper_out_valid
            node.io.lower_l_in_valid    := firstLayerNodes((i << 1) + 1).io.upper_out_valid
        }}

        // upper upper layer
        if ((sourceCountUp >> 3) != 0) {

            var treeWidth: Int = sourceCountUp >> 3
            var treeLayer: Int = 1

            while (treeWidth > 0) {

                upperLayerNodes(treeLayer) = new Array[NodeUpperLayer](treeWidth)

                for (i <- 0 until upperLayerNodes(treeLayer).length) 
                    upperLayerNodes(treeLayer)(i) = Module(new NodeUpperLayer(treeLayer, i))
                
                upperLayerNodes(treeLayer).zipWithIndex.foreach { case (node, i) => {

                    upperLayerNodes(treeLayer - 1)((i << 1) + 0).io.upper_in_select := node.io.lower_r_out_select
                    upperLayerNodes(treeLayer - 1)((i << 1) + 1).io.upper_in_select := node.io.lower_l_out_select

                    node.io.lower_r_in_index    := upperLayerNodes(treeLayer - 1)((i << 1) + 0).io.upper_out_index
                    node.io.lower_l_in_index    := upperLayerNodes(treeLayer - 1)((i << 1) + 1).io.upper_out_index

                    node.io.lower_r_in_valid    := upperLayerNodes(treeLayer - 1)((i << 1) + 0).io.upper_out_valid
                    node.io.lower_l_in_valid    := upperLayerNodes(treeLayer - 1)((i << 1) + 1).io.upper_out_valid
                }}

                treeWidth >>= 1
                treeLayer  += 1
            }
        }

        upperLayerNodes.last(0).io.upper_in_select  := io.in_head

        io.out_valid    := upperLayerNodes.last(0).io.upper_out_valid
        io.out_index    := upperLayerNodes.last(0).io.upper_out_index
    } else {

        firstLayerNodes(0).io.upper_in_select   := io.in_head(0)

        io.out_valid    := firstLayerNodes(0).io.upper_out_valid
        io.out_index    := firstLayerNodes(0).io.upper_out_index
    }
}
