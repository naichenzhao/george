// See LICENSE for license details.
package chipyard.fpga.fletcherlake

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.SBUS
import sifive.fpgashells.shell.DesignKey
import testchipip.soc.OBUS

// Do not use FPGA Shell's DesignKey.
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

// Initial Fletcher Lake bring-up assumes the Bora Lake FPGA pinout. Keep the
// platform-specific binders separate so the two can evolve independently.
class WithFletcherLakeTweaks(freqMHz: Double = 20) extends Config(
  new WithFletcherLakeUARTTSI ++
  new WithFletcherLakeTDDRTL ++

  new WithNoDesignKey ++
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = 921600) ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++

  new chipyard.config.WithTLBackingMemory ++
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(4) << 30) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class FletcherLakeRocketConfig extends Config(
  new WithFletcherLakeTweaks(freqMHz = 50) ++
  new testchipip.serdes.WithNoSerialTL ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.RocketConfig)

// Bring-up prototype for the DSP24 chip.
class FletcherLakeDSP24Config extends Config(
  new chipyard.iobinders.WithI2CIOCells ++
  new WithFletcherLakeRegulatorI2C ++
  new chipyard.config.WithI2C(address = 0x10081000) ++

  // Independent RP2350-facing modern SerialTL link.  Do not change the two
  // fixed-width legacy SerialTL links connected to the physical DSP24 below.
  new WithFletcherLakeRP2350SerialTLToGPIO ++
  new testchipip.serdes.WithSerialTL(Seq(
    testchipip.serdes.SerialTLParams(
      client = Some(testchipip.serdes.SerialTLClientParams(totalIdBits = 4)),
      manager = None,
      phyParams = testchipip.serdes.DecoupledInternalSyncSerialPhyParams(
        phitWidth = 4,
        flitWidth = 16,
        freqMHz = 5)
    )
  )) ++

  new WithFletcherLakeTweaks(freqMHz = 10) ++
  new WithFletcherLakeDSP24SerialTLToGPIO ++
  new chipyard.iobinders.WithOldSerialTLPunchthrough ++

  new testchipip.serdes.old.WithSerialTL(Seq(
    testchipip.serdes.old.SerialTLParams(
      manager = Some(testchipip.serdes.old.SerialTLManagerParams(
        memParams = Seq(
          testchipip.serdes.old.ManagerRAMParams(
            address = BigInt("00000000", 16),
            size    = BigInt("10070000", 16)),
          testchipip.serdes.old.ManagerRAMParams(
            address = BigInt("14000000", 16),
            size    = BigInt("6C000000", 16))
        ))),
      client = Some(testchipip.serdes.old.SerialTLClientParams()),
      phyParams = testchipip.serdes.old.InternalSyncSerialParams(width = 8, freqMHz = 10)),
    testchipip.serdes.old.SerialTLParams(
      manager = None,
      client = Some(testchipip.serdes.old.SerialTLClientParams()),
      phyParams = testchipip.serdes.old.InternalSyncSerialParams(width = 1, freqMHz = 10))
  )) ++

  new testchipip.soc.WithOffchipBusClient(
    SBUS,
    blockRange = AddressSet.misaligned(0x100000000L, (BigInt(1) << 30) * 4)) ++
  new testchipip.soc.WithOffchipBus ++
  new chipyard.NoCoresConfig)

// DSP25 bring-up plus an on-FPGA 64 KiB scratchpad at 0x1_0000_0000 that
// UART-TSI can reach without the DDR3 controller: separates "UART-TSI link is
// dead" from "DDR never calibrated" (which also shows on FPGA_LED3).  ExtMem is
// shrunk to 2 GiB so the scratchpad sits above it inside the on-FPGA address
// window; everything below 0x8000_0000 still goes off-chip to the DSP25.
class FletcherLakeDSP25DiagConfig extends Config(
  new testchipip.soc.WithMbusScratchpad(base = 0x100000000L, size = 64 << 10) ++
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(2) << 30) ++
  new FletcherLakeDSP25Config)

// Bring-up prototype for the DSP25 chip.
class FletcherLakeDSP25Config extends Config(
  new WithFletcherLakeDSP25SerialTLToGPIO ++
  new WithFletcherLakeTweaks(freqMHz = 50) ++
  new chipyard.iobinders.WithOldSerialTLPunchthrough ++

  new testchipip.serdes.WithSerialTL(Seq(
    testchipip.serdes.SerialTLParams(
      manager = Some(testchipip.serdes.SerialTLManagerParams(
        memParams = Seq(testchipip.serdes.ManagerRAMParams(
          address = BigInt("00000000", 16),
          size    = BigInt("80000000", 16)
        )),
        slaveWhere = OBUS
      )),
      client = Some(testchipip.serdes.SerialTLClientParams()),
      phyParams = testchipip.serdes.DecoupledInternalSyncSerialPhyParams(
        phitWidth = 8,
        flitWidth = 16,
        freqMHz = 50)
    )
  )) ++

  new testchipip.soc.WithOffchipBusClient(
    SBUS,
    blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++
  new testchipip.soc.WithOffchipBus ++
  new chipyard.NoCoresConfig)
