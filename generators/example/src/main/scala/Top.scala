package example

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import sifive.blocks.devices.gpio._

import icenet.{HasPeripheryIceNIC, HasPeripheryIceNICModuleImp}

import memblade.client.{HasPeripheryRemoteMemClient, HasPeripheryRemoteMemClientModuleImp}
import memblade.cache.{HasPeripheryDRAMCache, HasPeripheryDRAMCacheModuleImp}

// -------------------------------
// BOOM and/or Rocket Top Level Systems
// -------------------------------

class BoomRocketTop(implicit p: Parameters) extends boom.system.BoomRocketSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomRocketTopModule(this)
}

class BoomRocketTopModule[+L <: BoomRocketTop](l: L) extends boom.system.BoomRocketSystemModule(l)
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class BoomRocketTopWithPWMTL(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryPWMTL {
  override lazy val module = new BoomRocketTopWithPWMTLModule(this)
}

class BoomRocketTopWithPWMTLModule(l: BoomRocketTopWithPWMTL) extends BoomRocketTopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomRocketTopWithPWMAXI4(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryPWMAXI4 {
  override lazy val module = new BoomRocketTopWithPWMAXI4Module(this)
}

class BoomRocketTopWithPWMAXI4Module(l: BoomRocketTopWithPWMAXI4) extends BoomRocketTopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomRocketTopWithBlockDevice(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryBlockDevice {
  override lazy val module = new BoomRocketTopWithBlockDeviceModule(this)
}

class BoomRocketTopWithBlockDeviceModule(l: BoomRocketTopWithBlockDevice) extends BoomRocketTopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomRocketTopWithGPIO(implicit p: Parameters) extends BoomRocketTop
    with HasPeripheryGPIO {
  override lazy val module = new BoomRocketTopWithGPIOModule(this)
}

class BoomRocketTopWithGPIOModule(l: BoomRocketTopWithGPIO)
  extends BoomRocketTopModule(l)
  with HasPeripheryGPIOModuleImp

class BoomRocketTopWithIceNIC(implicit p: Parameters) extends BoomRocketTop
    with HasPeripheryIceNIC {
  override lazy val module = new BoomRocketTopWithIceNICModule(this)
}

class BoomRocketTopWithIceNICModule(outer: BoomRocketTopWithIceNIC)
  extends BoomRocketTopModule(outer)
  with HasPeripheryIceNICModuleImp

class BoomRocketTopWithRemoteMemClient(implicit p: Parameters)
    extends BoomRocketTop
    with HasPeripheryRemoteMemClient {
  override lazy val module = new BoomRocketTopWithRemoteMemClientModule(this)
}

class BoomRocketTopWithRemoteMemClientModule(outer: BoomRocketTopWithRemoteMemClient)
  extends BoomRocketTopModule(outer)
  with HasPeripheryRemoteMemClientModuleImp

class BoomRocketTopWithDRAMCache(implicit p: Parameters)
    extends BoomRocketTop
    with HasPeripheryDRAMCache {
  override lazy val module = new BoomRocketTopWithDRAMCacheModule(this)
}

class BoomRocketTopWithDRAMCacheModule(outer: BoomRocketTopWithDRAMCache)
  extends BoomRocketTopModule(outer)
  with HasPeripheryDRAMCacheModuleImp
