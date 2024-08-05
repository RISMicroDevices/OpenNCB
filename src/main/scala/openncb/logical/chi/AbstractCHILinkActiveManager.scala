package cn.rismd.openncb.logical.chi

import chisel3._
import cn.rismd.openncb.chi.WithCHIParameters
import org.chipsalliance.cde.config.Parameters

abstract class AbstractCHILinkActiveManager(implicit val p: Parameters)
               extends Module with WithCHIParameters

