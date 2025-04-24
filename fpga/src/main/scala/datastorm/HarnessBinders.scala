package chipyard.fpga.datastorm

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
import sifive.fpgashells.ip.altera._
import sifive.fpgashells.shell.altera._
import sifive.fpgashells.clocks._
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import testchipip.serdes._

class WithDatastormDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val bundles = ath.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithDatastormSerialTLToFMC extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OldSerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io

    harnessIO match {
      case io: testchipip.serdes.old.DecoupledSerialIO => {
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          ("PIN_D11", clkIO), //LA21_P
          ("PIN_H7", IOPin(io.out.valid)), //LA29_N
          ("PIN_D1", IOPin(io.out.ready)), //LA32_N
          ("PIN_J7", IOPin(io.in.valid)),
          ("PIN_B3", IOPin(io.in.ready)),
          ("PIN_C3", IOPin(io.out.bits, 0)),
          ("PIN_G10", IOPin(io.out.bits, 1)),
          ("PIN_D10", IOPin(io.out.bits, 2)),
          ("PIN_E1", IOPin(io.out.bits, 3)),
          ("PIN_G8", IOPin(io.out.bits, 4)),
          ("PIN_J9", IOPin(io.out.bits, 5)),
          ("PIN_A4", IOPin(io.out.bits, 6)),
          ("PIN_J10", IOPin(io.out.bits, 7)),
          ("PIN_H8", IOPin(io.in.bits, 0)),
          ("PIN_F10", IOPin(io.in.bits, 1)),
          ("PIN_K7", IOPin(io.in.bits, 2)),
          ("PIN_K8", IOPin(io.in.bits, 3)),
          ("PIN_A3", IOPin(io.in.bits, 4)),
          ("PIN_C2", IOPin(io.in.bits, 5)),
          ("PIN_G12", IOPin(io.in.bits, 6)),
          ("PIN_D2", IOPin(io.in.bits, 7))
        )
        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.io_tcl.addPackagePin(io, pin)
          ath.io_tcl.addIOStandard(io, "1.2 V")
        }}

        ath.sdc.addClock("ser_tl_clock", clkIO, 50)
        ath.sdc.addGroup(clocks = Seq("ser_tl_clock"))
      }
    }
  }
})

class WithDatastormUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("PIN_AG10" , IOPin(harnessIO.rxd)),
      ("PIN_AH9", IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "1.5 V")
    } }
  }
})

// Maps the UART device to the on-board USB-UART
class WithDatastormUART(rxdPin: String = "PIN_AG10", txdPin: String = "PIN_AH9") extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("uart")
    harnessIO <> port.io
    val packagePinsWithPackageIOs = Seq(
      (rxdPin, IOPin(harnessIO.rxd)),
      (txdPin, IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "3.3-V LVTTL")
    } }
  }
})

// Maps the UART device to PMOD JD pins 3/7
class WithDatastormPMODUART extends WithDatastormUART("PIN_AB12", "PIN_AC12")

class WithDatastormJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[DatastormHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("jtag")
    harnessIO <> port.io

    ath.sdc.addClock("JTCK", IOPin(harnessIO.TCK), 10)
    ath.sdc.addGroup(clocks = Seq("JTCK"))
    val packagePinsWithPackageIOs = Seq(
      ("PIN_AD12", IOPin(harnessIO.TCK)),
      ("PIN_AD10", IOPin(harnessIO.TMS)),
      ("PIN_AC9", IOPin(harnessIO.TDI)),
      ("PIN_AD9", IOPin(harnessIO.TDO))
    )
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.io_tcl.addPackagePin(io, pin)
      ath.io_tcl.addIOStandard(io, "3.3-V LVTTL")
      // TODO Check if Cyclone V devices have integrated pullups ath.io_tcl.addPullup(io)
    } }
  }
})