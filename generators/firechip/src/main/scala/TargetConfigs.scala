package firesim.firesim

import java.io.File

import chisel3._
import chisel3.util.{log2Up}
// import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.config._
import freechips.rocketchip.groundtest.TraceGenParams
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams}
import freechips.rocketchip.devices.debug.{DebugModuleParams, DebugModuleKey}
import freechips.rocketchip.diplomacy.{LazyModule, AsynchronousCrossing}
import testchipip.{BlockDeviceKey, BlockDeviceConfig, TracePortKey, TracePortParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import scala.math.{min, max}

import boom.common._
import chipyard.clocking.{ChipyardPRCIControlKey}
import icenet._

import firesim.bridges._
import firesim.configs._

import constellation.channel._
import constellation.routing._
import constellation.topology._
import constellation.noc._
import constellation.soc.{GlobalNoCParams}

import messagequeue._

import scala.collection.immutable.ListMap

class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => {
    val chipyardBootROM = new File(s"./generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")
    val firesimBootROM = new File(s"./target-rtl/chipyard/generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")

    val bootROMPath = if (chipyardBootROM.exists()) {
      chipyardBootROM.getAbsolutePath()
    } else {
      firesimBootROM.getAbsolutePath()
    }
    up(BootROMLocated(x), site).map(_.copy(contentFileName = bootROMPath))
  }
})

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey, site).copy(enableTileClockGating = false)
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
  case TracePortKey => up(TracePortKey, site).map(_.copy(print = true))
})

// FASED Config Aliases. This to enable config generation via "_" concatenation
// which requires that all config classes be defined in the same package
class DDR3FCFS extends FCFS16GBQuadRank
class DDR3FRFCFS extends FRFCFS16GBQuadRank
class DDR3FRFCFSLLC4MB extends FRFCFS16GBQuadRankLLC4MB

class WithNIC extends icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64)

// Adds a small/large NVDLA to the system
class WithNVDLALarge extends nvidia.blocks.dla.WithNVDLA("large")
class WithNVDLASmall extends nvidia.blocks.dla.WithNVDLA("small")

// Minimal set of FireSim-related design tweaks - notably discludes FASED, TraceIO, and the BlockDevice
class WithMinimalFireSimDesignTweaks extends Config(
  // Required*: Uses FireSim ClockBridge and PeekPokeBridge to drive the system with a single clock/reset
  new WithFireSimHarnessClockBinder ++
  new WithFireSimSimpleClocks ++
  // Required*: When using FireSim-as-top to provide a correct path to the target bootrom source
  new WithBootROM ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new WithoutClockGating ++
  // Required*: Removes thousands of assertions that would be synthesized (* pending PriorityMux bugfix)
  new WithoutTLMonitors ++
  // Required: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new chipyard.config.WithNoDebug
)

// Non-frequency tweaks that are generally applied to all firesim configs
class WithFireSimDesignTweaks extends Config(
  new WithMinimalFireSimDesignTweaks ++
  // Required: Bake in the default FASED memory model
  new WithDefaultMemModel ++
  // Optional: reduce the width of the Serial TL interface
  new testchipip.WithSerialTLWidth(4) ++
  // Required*: Scale default baud rate with periphery bus frequency
  new chipyard.config.WithUART(BigInt(3686400L)) ++
  // Optional: Adds IO to attach tracerV bridges
  new chipyard.config.WithTraceIO ++
  // Optional: Request 16 GiB of target-DRAM by default (can safely request up to 32 GiB on F1)
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 16L) ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.WithBlockDevice
)

// Tweaks to modify target clock frequencies / crossings to legacy firesim defaults
class WithFireSimHighPerfClocking extends Config(
  // Optional: This sets the default frequency for all buses in the system to 3.2 GHz
  // (since unspecified bus frequencies will use the pbus frequency)
  // This frequency selection matches FireSim's legacy selection and is required
  // to support 200Gb NIC performance. You may select a smaller value.
  new chipyard.config.WithPeripheryBusFrequency(3200.0) ++
  // Optional: These three configs put the DRAM memory system in it's own clock domain.
  // Removing the first config will result in the FASED timing model running
  // at the pbus freq (above, 3.2 GHz), which is outside the range of valid DDR3 speedgrades.
  // 1 GHz matches the FASED default, using some other frequency will require
  // runnings the FASED runtime configuration generator to generate faithful DDR3 timing values.
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new chipyard.config.WithAsynchrousMemoryBusCrossing ++
  new testchipip.WithAsynchronousSerialSlaveCrossing
)

// Tweaks that are generally applied to all firesim configs setting a single clock domain at 1000 MHz
class WithFireSimConfigTweaks extends Config(
  // 1 GHz matches the FASED default (DRAM modeli realistically configured for that frequency)
  // Using some other frequency will require runnings the FASED runtime configuration generator
  // to generate faithful DDR3 timing values.
  new chipyard.config.WithSystemBusFrequency(1000.0) ++
  new chipyard.config.WithSystemBusFrequencyAsDefault ++ // All unspecified clock frequencies, notably the implicit clock, will use the sbus freq (1000 MHz)
  // Explicitly set PBUS + MBUS to 1000 MHz, since they will be driven to 100 MHz by default because of assignments in the Chisel
  new chipyard.config.WithPeripheryBusFrequency(1000.0) ++
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new WithFireSimDesignTweaks
)

// Tweak more representative of testchip configs
class WithFireSimTestChipConfigTweaks extends Config(
  new chipyard.config.WithTestChipBusFreqs ++
  new WithFireSimDesignTweaks
)

// Tweaks to use minimal design tweaks
// Need to use initramfs to use linux (no block device)
class WithMinimalFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.WithBackingScratchpad ++
  new WithMinimalFireSimDesignTweaks
)

/**
  * Adds BlockDevice to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndBlockDeviceFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++ // removes mem port for FASEDBridge to match against
  new testchipip.WithBackingScratchpad ++ // adds backing scratchpad for memory to replace FASED model
  new testchipip.WithBlockDevice(true) ++ // add in block device
  new WithMinimalFireSimDesignTweaks
)

/**
  *  Adds Block device to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndFASEDFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new WithDefaultMemModel ++ // add default FASED memory model
  new WithMinimalFireSimDesignTweaks
)

// Tweaks for legacy FireSim configs.
class WithFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new WithFireSimDesignTweaks
)

/*******************************************************************************
* Full TARGET_CONFIG configurations. These set parameters of the target being
* simulated.
*
* In general, if you're adding or removing features from any of these, you
* should CREATE A NEW ONE, WITH A NEW NAME. This is because the manager
* will store this name as part of the tags for the AGFI, so that later you can
* reconstruct what is in a particular AGFI. These tags are also used to
* determine which driver to build.
 *******************************************************************************/

//***************************************************************** 
// Rocket configs, base off chipyard's RocketConfig
//*****************************************************************
// DOC include start: firesimconfig
class FireSimRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)
// DOC include end: firesimconfig

class FireSimQuadRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.QuadRocketConfig)

// A stripped down configuration that should fit on all supported hosts.
// Flat to avoid having to reorganize the config class hierarchy to remove certain features
class FireSimSmallSystemConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithBootROM ++
  new chipyard.config.WithPeripheryBusFrequency(3200.0) ++
  new WithoutClockGating ++
  new WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithExtMemSize(1 << 28) ++
  new testchipip.WithDefaultSerialTL ++
  new testchipip.WithBlockDevice ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays = 2, capacityKB = 64) ++
  new chipyard.RocketConfig)

//*****************************************************************
// Boom config, base off chipyard's LargeBoomConfig
//*****************************************************************
class FireSimLargeBoomConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomConfig)

//********************************************************************
// Heterogeneous config, base off chipyard's LargeBoomAndRocketConfig
//********************************************************************
class FireSimLargeBoomAndRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomAndRocketConfig)

//******************************************************************
// Gemmini NN accel config, base off chipyard's GemminiRocketConfig
//******************************************************************
class FireSimGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.GemminiRocketConfig)

class FireSimLeanGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)

class FireSimLeanGemminiPrintfRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiPrintfRocketConfig)

//**********************************************************************************
// Supernode Configurations, base off chipyard's RocketConfig
//**********************************************************************************
class SupernodeFireSimRocketConfig extends Config(
  new WithNumNodes(4) ++
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 8L) ++ // 8 GB
  new FireSimRocketConfig)

//**********************************************************************************
//* CVA6 Configurations
//*********************************************************************************/
class FireSimCVA6Config extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.CVA6Config)

//**********************************************************************************
//* Multiclock Configurations
//*********************************************************************************/
class FireSimMulticlockAXIOverSerialConfig extends Config(
  new WithAXIOverSerialTLCombinedBridges ++ // use combined bridge to connect to axi mem over serial
  new WithDefaultFireSimBridges ++
  new testchipip.WithBlockDevice(false) ++ // disable blockdev
  new WithDefaultMemModel ++
  new WithFireSimDesignTweaks ++ // don't inherit firesim clocking
  new chipyard.MulticlockAXIOverSerialConfig
)

//**********************************************************************************
// System with 16 LargeBOOMs that can be simulated with Golden Gate optimizations
// - Requires MTModels and MCRams mixins as prefixes to the platform config
// - May require larger build instances or JVM memory footprints
//*********************************************************************************/
class FireSim16LargeBoomConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new boom.common.WithNLargeBooms(16) ++
  new chipyard.config.AbstractConfig)

class FireSimNoMemPortConfig extends Config(
  new WithDefaultFireSimBridges ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.WithBackingScratchpad ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimLeanGemminiRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)

class FireSimMessageQueueConfig4CoreMesh extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new messagequeue.WithMessageQueueNoC(messagequeue.MQNoCProtocolParams(
    hartMappings = ListMap( // naively map hartIds to the same nodeId
      0 -> 0,
      1 -> 1,
      2 -> 2,
      3 -> 3),
    nocParams = NoCParams(
      topology = Mesh2D(2, 2),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(3) { UserVirtualChannelParams(2) }), // 3 VCs/channel, 2 buffer slots/VC
      routingRelation = Mesh2DDimensionOrderedRouting()
    )
  )) ++
  new messagequeue.WithMessageQueue ++
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++
  new chipyard.config.AbstractConfig
  )

class ConstellationTestConfig extends Config(
 new WithDefaultMMIOOnlyFireSimBridges ++
 new WithFireSimConfigTweaks ++
 new chipyard.SbusRingNoCConfig
  // new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
  //   constellation.protocol.DiplomaticNetworkNodeMapping(
  //     inNodeMapping = ListMap(
  //       "Core 0" -> 0,
  //       "Core 1" -> 1,
  //       "serial-tl" -> 3),
  //     outNodeMapping = ListMap(
  //       "system[0]" -> 2,
  //       "pbus" -> 3)), // TSI is on the pbus, so serial-tl and pbus should be on the same node
  //   NoCParams(
  //     topology        = UnidirectionalTorus1D(4),
  //     channelParamGen = (a, b) => UserChannelParams(Seq.fill(10) { UserVirtualChannelParams(4) }),
  //     routingRelation = NonblockingVirtualSubnetworksRouting(UnidirectionalTorus1DDatelineRouting(), 5, 2))
  // )) ++
  // new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  // new freechips.rocketchip.subsystem.WithNBanks(1) ++
  // new chipyard.config.AbstractConfig
)

class ConstellationTestConfigBoom extends Config(
 new WithDefaultMMIOOnlyFireSimBridges ++
 new WithFireSimConfigTweaks ++
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "Core 0" -> 0,
        "Core 1" -> 1,
        "serial-tl" -> 3),
      outNodeMapping = ListMap(
        "system[0]" -> 2,
        "pbus" -> 3)), // TSI is on the pbus, so serial-tl and pbus should be on the same node
    NoCParams(
      topology        = UnidirectionalTorus1D(4),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(10) { UserVirtualChannelParams(4) }),
      routingRelation = NonblockingVirtualSubnetworksRouting(UnidirectionalTorus1DDatelineRouting(), 5, 2))
  )) ++ 
  new boom.common.WithNSmallBooms(2) ++
  // new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithNBanks(1) ++
  new chipyard.config.AbstractConfig
)
// class ConstellationTestConfig extends Config(
//   new WithDefaultMMIOOnlyFireSimBridges ++
//   new WithDefaultMemModel ++
//   new WithFireSimConfigTweaks ++
//   new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
//     constellation.protocol.DiplomaticNetworkNodeMapping(
//       inNodeMapping = ListMap(
//         "Core 0" -> 0,
//         "Core 1" -> 1,
//         "Core 2" -> 2,
//         "Core 3" -> 6,
//         "Core 4" -> 7,
//         "Core 5" -> 8,
//         "serial-tl" -> 4),
//       outNodeMapping = ListMap(
//         "system[0]" -> 3,
//         "system[1]" -> 5,
//         "pbus" -> 4)), // TSI is on the pbus, so serial-tl and pbus should be on the same node
//     NoCParams(
//       topology        = Mesh2D(3, 3),
//       channelParamGen = (a, b) => UserChannelParams(Seq.fill(10) { UserVirtualChannelParams(4) }),
//       routingRelation = NonblockingVirtualSubnetworksRouting(Mesh2DDimensionOrderedRouting(), 5, 2))
//   )) ++
//   new freechips.rocketchip.subsystem.WithNSmallCores(6) ++
//   new freechips.rocketchip.subsystem.WithNBanks(2) ++
//   new chipyard.config.AbstractConfig
// )
//

class MegaBoomFireSimConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new boom.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class OctaCoreRocketFireSimConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new freechips.rocketchip.subsystem.WithNBigCores(8) ++
  new chipyard.config.AbstractConfig
  )
