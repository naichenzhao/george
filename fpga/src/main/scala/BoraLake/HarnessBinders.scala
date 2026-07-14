package chipyard.fpga.boralake

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

class WithBoraLakeUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[BoraLakeHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("D23" , IOPin(harnessIO.rxd)),
      ("D24", IOPin(harnessIO.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addIOB(io)
    } }
  }
})



class WithBoraLakeTDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[BoraLakeHarness]
    val bundles = ath.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithDSP25SerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[BoraLakeHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io
    harnessIO match {
      case io: DecoupledPhitIO => {
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }

        val packagePinsWithPackageIOs = {
          Seq(
            ("E17", clkIO),

            ("F12", IOPin(io.in.valid)),
            ("G16", IOPin(io.in.ready)),
            ("E15", IOPin(io.in.bits.phit, 0)),
            ("F14", IOPin(io.in.bits.phit, 1)),
            ("F15", IOPin(io.in.bits.phit, 2)),
            ("C12", IOPin(io.in.bits.phit, 3)),
            ("B11", IOPin(io.in.bits.phit, 4)),
            ("C9",  IOPin(io.in.bits.phit, 5)),
            ("G15", IOPin(io.in.bits.phit, 6)),
            ("G12", IOPin(io.in.bits.phit, 7)),

            ("D14", IOPin(io.out.valid)),
            ("C14", IOPin(io.out.ready)),
            ("C16", IOPin(io.out.bits.phit, 0)),
            ("G17", IOPin(io.out.bits.phit, 1)),
            ("D8",  IOPin(io.out.bits.phit, 2)),
            ("J11", IOPin(io.out.bits.phit, 3)),
            ("H9",  IOPin(io.out.bits.phit, 4)),
            ("H13", IOPin(io.out.bits.phit, 5)),
            ("C17", IOPin(io.out.bits.phit, 6)),
            ("H8",  IOPin(io.out.bits.phit, 7)),
          )
        }

        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.xdc.addPackagePin(io, pin)
          ath.xdc.addIOStandard(io, "LVCMOS12")
        }}

        // Don't add IOB to the clock, if its an input
        io match {
          case io: DecoupledInternalSyncPhitIO => packagePinsWithPackageIOs foreach { case (pin, io) => {
            ath.xdc.addIOB(io)
          }}
          case io: DecoupledExternalSyncPhitIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) => {
            ath.xdc.addIOB(io)
          }}
        }

        ath.sdc.addClock("ser_tl_clock", clkIO, 100)
        ath.sdc.addGroup(pins = Seq(clkIO))
        ath.xdc.clockDedicatedRouteFalse(clkIO)
      }
    }
  }
})

class WithDSP24SerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OldSerialTLPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[BoraLakeHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName(s"serial_tl_old_${port.portId}")
    harnessIO <> port.io
    harnessIO match {
      case io: testchipip.serdes.old.DecoupledSerialIO => {
        val clkIO = io match {
          case io: testchipip.serdes.old.InternalSyncSerialIO => IOPin(io.clock_out)
          case io: testchipip.serdes.old.ExternalSyncSerialIO => IOPin(io.clock_in)
        }


        val packagePinsWithPackageIOs = if (port.portId == 0) {
          Seq(
            ("J11", clkIO),
            ("F12", IOPin(io.in.valid)),
            ("H12", IOPin(io.in.ready)),
            ("F8", IOPin(io.in.bits, 0)),
            ("F10", IOPin(io.in.bits, 1)),
            ("D9", IOPin(io.in.bits, 2)),
            ("A9", IOPin(io.in.bits, 3)),
            ("E11", IOPin(io.in.bits, 4)),
            ("C9", IOPin(io.in.bits, 5)),
            ("H14", IOPin(io.in.bits, 6)),
            ("J13", IOPin(io.in.bits, 7)),

            ("E10", IOPin(io.out.valid)),
            ("A10", IOPin(io.out.ready)),
            ("G10", IOPin(io.out.bits, 0)),
            ("F9", IOPin(io.out.bits, 1)),
            ("D8", IOPin(io.out.bits, 2)),
            ("G12", IOPin(io.out.bits, 3)),
            ("D11", IOPin(io.out.bits, 4)),
            ("G11", IOPin(io.out.bits, 5)),
            ("B10", IOPin(io.out.bits, 6)),
            ("H8", IOPin(io.out.bits, 7)),
          )
        } else {
          Seq(

            ("E13", clkIO),
            ("H13", IOPin(io.out.valid)),
            ("C11", IOPin(io.out.ready)),
            ("H9", IOPin(io.out.bits, 0)),
            ("A8", IOPin(io.in.valid)),
            ("G9", IOPin(io.in.ready)),
            ("D10", IOPin(io.in.bits, 0)),
          )
        }

        packagePinsWithPackageIOs foreach { case (pin, io) => {
          ath.xdc.addPackagePin(io, pin)
          ath.xdc.addIOStandard(io, "LVCMOS12")
        }}

        // Don't add IOB to the clock, if its an input
        io match {
          case io: testchipip.serdes.old.InternalSyncSerialIO => packagePinsWithPackageIOs foreach { case (pin, io) => {
            ath.xdc.addIOB(io)
          }}
          case io: testchipip.serdes.old.ExternalSyncSerialIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) => {
            ath.xdc.addIOB(io)
          }}
        }

        ath.sdc.addClock("ser_tl_clock", clkIO, 100)
        ath.sdc.addGroup(pins = Seq(clkIO))
        ath.xdc.clockDedicatedRouteFalse(clkIO)
      }
    }
  }
})

class WithRegulatorI2C extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: I2CPinsPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[BoraLakeHarness]
    val harnessIO = IO(new sifive.blocks.devices.i2c.I2CPinsIO).suggestName("john_i2c")
    harnessIO <> port.io
    val packagePinsWithPackageIOs = Seq(
      ("C21" , IOPin(harnessIO.scl)),
      ("B21", IOPin(harnessIO.sda)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.xdc.addPackagePin(io, pin)
      ath.xdc.addIOStandard(io, "LVCMOS33")
      ath.xdc.addIOB(io)
      ath.xdc.addPullup(io)
    } }
  }
})
