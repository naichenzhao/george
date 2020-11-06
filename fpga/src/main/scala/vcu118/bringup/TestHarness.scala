package chipyard.fpga.vcu118.bringup

import chisel3._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import testchipip.{HasPeripheryTSIHostWidget, PeripheryTSIHostKey, TSIHostWidgetIO, TLSinkSetter}

import chipyard.fpga.vcu118.{VCU118FPGATestHarness, VCU118FPGATestHarnessImp}

import chipyard.{ChipTop}

class BringupVCU118FPGATestHarness(override implicit val p: Parameters) extends VCU118FPGATestHarness {

  /*** UART ***/

  require(dp(PeripheryUARTKey).size == 2)

  // 2nd UART goes to the FMC UART

  val uart_fmc = Overlay(UARTOverlayKey, new BringupUARTVCU118ShellPlacer(this, UARTShellInput()))

  val io_fmc_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).last)))
  dp(UARTOverlayKey).last.place(UARTDesignInput(io_fmc_uart_bb))

  /*** SPI ***/

  require(dp(PeripherySPIKey).size == 2)

  // 2nd SPI goes to the ADI port

  val adi = Overlay(SPIOverlayKey, new BringupSPIVCU118ShellPlacer(this, SPIShellInput()))

  val io_adi_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).last)))
  dp(SPIOverlayKey).last.place(SPIDesignInput(dp(PeripherySPIKey).last, io_adi_spi_bb))

  /*** I2C ***/

  val i2c = Overlay(I2COverlayKey, new BringupI2CVCU118ShellPlacer(this, I2CShellInput()))

  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  dp(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))

  /*** GPIO ***/

  val gpio = Seq.tabulate(dp(PeripheryGPIOKey).size)(i => {
    val maxGPIOSupport = 32 // max gpio per gpio chip
    val names = BringupGPIOs.names.slice(maxGPIOSupport*i, maxGPIOSupport*(i+1))
    Overlay(GPIOOverlayKey, new BringupGPIOVCU118ShellPlacer(this, GPIOShellInput(), names))
  })

  val io_gpio_bb = dp(PeripheryGPIOKey).map { p => BundleBridgeSource(() => (new GPIOPortIO(p))) }
  (dp(GPIOOverlayKey) zip dp(PeripheryGPIOKey)).zipWithIndex.map { case ((placer, params), i) =>
    placer.place(GPIODesignInput(params, io_gpio_bb(i)))
  }

  /*** TSI Host Widget ***/
  require(dp(PeripheryTSIHostKey).size == 1)

  val tsi_host = Overlay(TSIHostOverlayKey, new BringupTSIHostVCU118ShellPlacer(this, TSIHostShellInput()))

  val io_tsi_serial_bb = BundleBridgeSource(() => (new TSIHostWidgetIO(dp(PeripheryTSIHostKey).head.serialIfWidth)))
  val tsiDdrPlaced = dp(TSIHostOverlayKey).head.place(TSIHostDesignInput(dutWrangler.node, harnessSysPLL, dp(PeripheryTSIHostKey).head, io_tsi_serial_bb))

  // connect 1 mem. channel to the FPGA DDR
  val inTsiParams = topDesign match { case td: ChipTop =>
    td.lazySystem match { case lsys: HasPeripheryTSIHostWidget =>
      lsys.tsiMemTLNodes.head.edges.in(0)
    }
  }
  val tsiDdrClient = TLClientNode(Seq(inTsiParams.master))
  (tsiDdrPlaced.overlayOutput.ddr
    := TLFragmenter(8,64,holdFirstDeny=true)
    := TLCacheCork()
    := TLAtomicAutomata(passthrough=false)
    := TLSinkSetter(64)
    := tsiDdrClient)

  // module implementation
  override lazy val module = new BringupVCU118FPGATestHarnessImp(this)
}

class BringupVCU118FPGATestHarnessImp(_outer: BringupVCU118FPGATestHarness) extends VCU118FPGATestHarnessImp(_outer) {
  lazy val bringupOuter = _outer
}
