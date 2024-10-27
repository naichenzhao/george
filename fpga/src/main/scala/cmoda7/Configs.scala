// See LICENSE for license details.
package chipyard.fpga.cmoda7

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

import testchipip.serdes.{SerialTLKey}

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
// The PMODUART HarnessBinder maps the actual UART device to JD pin
class WithCmodA7Tweaks(freqMHz: Double = 50) extends Config(
  new WithCmodA7PMODUART ++
  new WithCmodA7UARTTSI ++
  new WithCmodA7JTAG ++
  new WithNoDesignKey ++
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = 921600) ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  
  // new freechips.rocketchip.subsystem.WithNoMemPort ++         // remove offchip mem port
  // new testchipip.serdes.WithNoSerialTL ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class TinyRocketCmodConfig extends Config(
  new WithCmodA7Tweaks(freqMHz=60) ++

  new riskybear.WithRobotJoint(address=0x181000000L) ++
  new WithCmodA7Joints ++

  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.TinyRocketConfig)

class NoCoresCmodA7Config extends Config(
  new WithCmodA7SerialTLToGPIO ++
  new WithCmodA7Tweaks(freqMHz=75) ++

  new riskybear.WithRobotJoint(address=0x181000000L) ++
  new WithCmodA7Joints ++

  new CMODA7ChipBringupHostConfig)


















// A simple config demonstrating a "bringup prototype" to bringup the ChipLikeRocketconfig
class CMODA7ChipBringupHostConfig extends Config(
  //=============================
  // Set up TestHarness for standalone-sim
  //=============================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++  // Generate absolute frequencies
  new chipyard.harness.WithSerialTLTiedOff ++                       // when doing standalone sim, tie off the serial-tl port
  new chipyard.harness.WithSimTSIToUARTTSI ++                       // Attach SimTSI-over-UART to the UART-TSI port
  new chipyard.iobinders.WithSerialTLPunchthrough ++                // Don't generate IOCells for the serial TL (this design maps to FPGA)

  //=============================
  // Setup the SerialTL side on the bringup device
  //=============================
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
    manager = Some(testchipip.serdes.SerialTLManagerParams(
      memParams = Seq(testchipip.serdes.ManagerRAMParams(                            // Bringup platform can access all memory from 0 to DRAM_BASE
        address = BigInt("00000000", 16),
        size    = BigInt("70000000", 16)
      ))
    )),
    client = Some(testchipip.serdes.SerialTLClientParams()),                                        // Allow chip to access this device's memory (DRAM)
    phyParams = testchipip.serdes.InternalSyncSerialPhyParams(phitWidth=1, flitWidth=16, freqMHz = 75) // bringup platform provides the clock
  ))) ++

  //============================
  // Setup bus topology on the bringup system
  //============================
  new testchipip.soc.WithOffchipBusClient(SBUS,                                // offchip bus hangs off the SBUS
    blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++ // offchip bus should not see the main memory of the testchip, since that can be accessed directly
  new testchipip.soc.WithOffchipBus ++                                         // offchip bus

  //=============================
  // Set up memory on the bringup system
  //=============================
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes the max size should be

  //=============================
  // Generate the TSI-over-UART side of the bringup system
  //=============================
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++       // nonstandard baud rate to improve performance

  //=============================
  // Set up clocks of the bringup system
  //=============================
  new chipyard.clocking.WithPassthroughClockGenerator ++ // pass all the clocks through, since this isn't a chip
  new chipyard.config.WithUniformBusFrequencies(75.0) ++   // run all buses of this system at 75 MHz

  // Base is the no-cores config
  new chipyard.NoCoresConfig)
