package chipyard.fpga.george

import chisel3._

import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.diplomacy.{LazyRawModuleImp}
import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import sifive.blocks.devices.uart.{UARTPortIO, UARTParams}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import testchipip.serdes._
import testchipip.serdes.old._

class WithGeorgeFPGAUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("D10" , IOPin(harnessIO.rxd)),
      ("A9", IOPin(harnessIO.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addIOB(io)
    } }
  }
})

class WithGeorgeFPGATDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val bundles = ath.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithGeorgeFPGASerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OldSerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io

    harnessIO match {
      case io: testchipip.serdes.old.DecoupledSerialIO => {
        val clkIO = io match {
          case io: testchipip.serdes.old.InternalSyncSerialIO => IOPin(io.clock_out)
          case io: testchipip.serdes.old.ExternalSyncSerialIO => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          // ("J18", clkIO),

          // ("D18", IOPin(io.in.valid)),
          // ("E18", IOPin(io.in.ready)),

          // ("C16", IOPin(io.in.bits, 0)),
          // ("A18", IOPin(io.in.bits, 1)),
          // ("D17", IOPin(io.in.bits, 2)),
          // ("H17", IOPin(io.in.bits, 3)),
          // ("B18", IOPin(io.in.bits, 4)),
          // ("E17", IOPin(io.in.bits, 5)),
          // ("A15", IOPin(io.in.bits, 6)),
          // ("A16", IOPin(io.in.bits, 7)),

          // ("J17", IOPin(io.out.valid)),
          // ("G17", IOPin(io.out.ready)),

          // ("E16", IOPin(io.out.bits, 0)),
          // ("C15", IOPin(io.out.bits, 1)),
          // ("D15", IOPin(io.out.bits, 2)),
          // ("G18", IOPin(io.out.bits, 3)),
          // ("C17", IOPin(io.out.bits, 4)),
          // ("F18", IOPin(io.out.bits, 5)),
          // ("C14", IOPin(io.out.bits, 6)),
          // ("B17", IOPin(io.out.bits, 7)),

          ("J17", clkIO),

          ("D15", IOPin(io.in.valid)),
          ("E16", IOPin(io.in.ready)),

          ("C16", IOPin(io.in.bits, 0)),
          ("A18", IOPin(io.in.bits, 1)),
          ("F18", IOPin(io.in.bits, 2)),
          ("H17", IOPin(io.in.bits, 3)),
          ("B18", IOPin(io.in.bits, 4)),
          ("E17", IOPin(io.in.bits, 5)),
          ("A15", IOPin(io.in.bits, 6)),
          ("B16", IOPin(io.in.bits, 7)),

          ("J18", IOPin(io.out.valid)),
          ("G17", IOPin(io.out.ready)),

          ("E18", IOPin(io.out.bits, 0)),
          ("C15", IOPin(io.out.bits, 1)),
          ("D18", IOPin(io.out.bits, 2)),
          ("G18", IOPin(io.out.bits, 3)),
          ("C17", IOPin(io.out.bits, 4)),
          ("D17", IOPin(io.out.bits, 5)),
          ("C14", IOPin(io.out.bits, 6)),
          ("B17", IOPin(io.out.bits, 7)),

        )
        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.xdc.addPackagePin(io, pin)
          ath.xdc.addIOStandard(io, "LVCMOS12")
        }}

        // // Don't add IOB to the clock, if its an input
        // io match {
        //   case io: testchipip.serdes.old.InternalSyncSerialIO => packagePinsWithPackageIOs foreach { case (pin, io) => {
        //     ath.xdc.addIOB(io)
        //   }}
        //   case io: testchipip.serdes.old.ExternalSyncSerialIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) => {
        //     ath.xdc.addIOB(io)
        //   }}
        // }

        ath.sdc.addClock("ser_tl_clock", clkIO, 100)
        ath.sdc.addGroup(pins = Seq(clkIO))
        ath.xdc.clockDedicatedRouteFalse(clkIO)
      }
    }
  }
})

// Maps the UART device to the on-board USB-UART
class WithGeorgeFPGAUART(rxdPin: String = "C10", txdPin: String = "A10") extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("uart")
    harnessIO <> port.io
    val packagePinsWithPackageIOs = Seq(
      (rxdPin, IOPin(harnessIO.rxd)),
      (txdPin, IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addIOB(io)
    } }
  }
})

// Maps the UART device to PMOD JD pins 3/7
class WithGeorgeFPGAPMODUART extends WithGeorgeFPGAUART("D9", "C9")

class WithGeorgeFPGAJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[GeorgeFPGAHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("jtag")
    harnessIO <> port.io

    ath.sdc.addClock("JTCK", IOPin(harnessIO.TCK), 10)
    ath.sdc.addGroup(clocks = Seq("JTCK"))
    ath.xdc.clockDedicatedRouteFalse(IOPin(harnessIO.TCK))
    val packagePinsWithPackageIOs = Seq(
      ("F4", IOPin(harnessIO.TCK)),
      ("D2", IOPin(harnessIO.TMS)),
      ("E2", IOPin(harnessIO.TDI)),
      ("D4", IOPin(harnessIO.TDO))
    )
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addPullup(io)
    } }
  }
})


