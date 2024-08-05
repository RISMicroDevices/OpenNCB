package cn.rismd.openncb.logical

import chisel3._
import cn.rismd.openncb.chi.WithCHIParameters
import org.chipsalliance.cde.config.Parameters

abstract class AbstractCHILinkCreditManager(implicit val p: Parameters)
               extends Module with WithCHIParameters
