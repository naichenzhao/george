// See LICENSE for license details.
package chipyard.fpga.george

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.{DesignKey}

import testchipip.serdes._

import chipyard.{BuildSystem}
import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.soc.{OBUS}

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

// By default, this uses the on-board USB-UART for the TSI-over-UART link
class WithGeorgeFPGATweaks(freqMHz: Double = 50) extends Config(
  new WithGeorgeFPGAPMODUART ++
  new WithGeorgeFPGAUARTTSI ++
  new WithGeorgeFPGAJTAG ++
  new WithNoDesignKey ++

  new testchipip.tsi.WithUARTTSIClient(initBaudRate = 921600) ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  
  new freechips.rocketchip.subsystem.WithNoMemPort ++         // remove offchip mem port
  new testchipip.serdes.WithNoSerialTL ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors)




class RocketGeorgeFPGAConfig extends Config(
  new WithGeorgeFPGASerialTLToGPIO ++
  new WithGeorgeFPGATweaks ++

  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.RocketConfig)





// This will fail to close timing above 50 MHz
// This will fail to close timing above 50 MHz
class BringupGeorgeFPGAConfig extends Config(
  new WithGeorgeFPGASerialTLToGPIO ++
  new WithGeorgeFPGATweaks(freqMHz = 50) ++
  
  new testchipip.serdes.WithSerialTLPHYParams(testchipip.serdes.InternalSyncSerialPhyParams(freqMHz=50)) ++

  new chipyard.ChipBringupHostConfig)







// class ChipBringupHostConfig extends Config(
//   //=============================
//   // Set up TestHarness for standalone-sim
//   //=============================
//   new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++  // Generate absolute frequencies
//   new chipyard.harness.WithSerialTLTiedOff ++                       // when doing standalone sim, tie off the serial-tl port
//   new chipyard.harness.WithSimTSIToUARTTSI ++                       // Attach SimTSI-over-UART to the UART-TSI port
//   new chipyard.iobinders.WithSerialTLPunchthrough ++                // Don't generate IOCells for the serial TL (this design maps to FPGA)

//   //=============================
//   // Setup the SerialTL side on the bringup device
//   //=============================
//   new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
//     manager = Some(testchipip.serdes.SerialTLManagerParams(
//       memParams = Seq(testchipip.serdes.ManagerRAMParams(                            // Bringup platform can access all memory from 0 to DRAM_BASE
//         address = BigInt("00000000", 16),
//         size    = BigInt("80000000", 16)
//       ))
//     )),
//     client = Some(testchipip.serdes.SerialTLClientParams()),                                        // Allow chip to access this device's memory (DRAM)
//     phyParams = testchipip.serdes.InternalSyncSerialPhyParams(phitWidth=8, flitWidth=16, freqMHz = 5) // bringup platform provides the clock
//   ))) ++

//   //============================
//   // Setup bus topology on the bringup system
//   //============================
//   new testchipip.soc.WithOffchipBusClient(SBUS,                                // offchip bus hangs off the SBUS
//     blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++ // offchip bus should not see the main memory of the testchip, since that can be accessed directly
//   new testchipip.soc.WithOffchipBus ++                                         // offchip bus

//   //=============================
//   // Set up memory on the bringup system
//   //=============================
//   new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes the max size should be

//   //=============================
//   // Generate the TSI-over-UART side of the bringup system
//   //=============================
//   new testchipip.tsi.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++       // nonstandard baud rate to improve performance

//   //=============================
//   // Set up clocks of the bringup system
//   //=============================
//   new chipyard.clocking.WithPassthroughClockGenerator ++ // pass all the clocks through, since this isn't a chip
//   new chipyard.config.WithUniformBusFrequencies(50.0) ++   // run all buses of this system at 75 MHz

//   // Base is the no-cores config
//   new chipyard.NoCoresConfig)


