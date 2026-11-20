// DSP24 legacy links plus a DDR-independent UART-TSI test scratchpad.
package chipyard.fpga.fletcherlake

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.SBUS

class FletcherLakeDSP24UART115200 extends Config(
  new chipyard.iobinders.WithI2CIOCells ++
  new WithFletcherLakeRegulatorI2C ++
  new chipyard.config.WithI2C(address = 0x10081000) ++

  new testchipip.serdes.WithNoSerialTL ++
  new testchipip.soc.WithMbusScratchpad(base = 0x180000000L, size = 64 << 10) ++

  // The 16x UART receiver needs at least 16 clocks per bit.
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = 115200) ++
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

// Separate build output preserves the original UART-fix artifact for comparison.
class FletcherLakeDSP24TimingFixed extends Config(new FletcherLakeDSP24UART115200)

class FletcherLakeDSP24CombinedFixed extends Config(
  new testchipip.soc.WithMbusScratchpad(base = 0x180000000L, size = 64 << 10) ++
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = 115200) ++
  new FletcherLakeDSP24Config)
