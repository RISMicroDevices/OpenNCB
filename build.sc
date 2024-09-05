import mill._
import scalalib._
import scalafmt._
import os.Path
import publish._
import $file.common
import $file.`external`.`rocket-chip`.common
import $file.`external`.`rocket-chip`.common
import $file.`external`.`rocket-chip`.cde.common
import $file.`external`.`rocket-chip`.hardfloat.build

val defaultVersions = Map(
  "chisel3" -> "3.6.0",
  "chisel3-plugin" -> "3.6.0",
  "chiseltest" -> "0.6.2",
  "scala" -> "2.13.10"
)

def getVersion(dep: String, org: String = "edu.berkeley.cs", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

trait HasChisel extends ScalaModule {
  def chiselModule: Option[ScalaModule] = None

  def chiselPluginJar: T[Option[PathRef]] = None

  def chiselIvy: Option[Dep] = Some(getVersion("chisel3"))

  def chiselPluginIvy: Option[Dep] = Some(getVersion("chisel3-plugin", cross=true))

  override def scalaVersion = defaultVersions("scala")

  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")

  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)
}

object rocketchip extends `external`.`rocket-chip`.common.RocketChipModule with HasChisel {

  val rcPath = os.pwd / "external" / "rocket-chip"
  override def millSourcePath = rcPath

  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.0"

  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.5"

  object macros extends `external`.`rocket-chip`.common.MacrosModule with HasChisel {
    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  }

  object cde extends `external`.`rocket-chip`.cde.common.CDEModule with HasChisel {
    override def millSourcePath = rcPath / "cde" / "cde"
  }

  object hardfloat extends `external`.`rocket-chip`.hardfloat.common.HardfloatModule with HasChisel {
    override def millSourcePath = rcPath / "hardfloat" / "hardfloat"
  }

  def macrosModule = macros

  def hardfloatModule = hardfloat

  def cdeModule = cde

}

object OpenNCB extends SbtModule with HasChisel with millbuild.common.OpenNCBModule {

  override def millSourcePath = millOuterCtx.millSourcePath

  def rocketModule: ScalaModule = rocketchip

  override def scalacOptions = super.scalacOptions() ++
    Agg("-deprecation")

  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      getVersion("chiseltest"),
    )
  }
}
